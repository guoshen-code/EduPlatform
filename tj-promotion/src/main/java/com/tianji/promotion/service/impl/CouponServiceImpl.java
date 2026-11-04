package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author author
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService couponScopeService;
    private final IExchangeCodeService codeService;
    private final IUserCouponService userCouponService;


    private final StringRedisTemplate redisTemplate;
    /*
    新增优惠券
     */
    @Override
    @Transactional // 两张表的新增（有个中间表）
    public void saveCoupon(CouponFormDTO dto) {
        // 1. 保存优惠券
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);

        // 2. 保存限定范围
        if(!dto.getSpecific()){
            return;
        }
        Long couponId = coupon.getId();
        List<Long> scopes = dto.getScopes();

        if (CollUtils.isEmpty(scopes)){
            throw new BadRequestException("优惠券范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(list);


    }
    /*
    分页查询优惠券
     */
    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        // 分页查询,过滤查询
        String name = query.getName();
        Integer status = query.getStatus();
        Integer type = query.getType();
        Page<Coupon> page = lambdaQuery()
                .eq(status != null, Coupon::getStatus, status)
                .eq(type != null, Coupon::getType, type)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 处理vo
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        List<CouponPageVO> vo = BeanUtils.copyList(records, CouponPageVO.class);
        // 返回
        return PageDTO.of(page, vo);
    }
    /*
    发放优惠券
     */
    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        // 查询优惠券信息
        Coupon coupon= getById(dto.getId());
        if (coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        // 判断状态
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE){
            throw new BadRequestException("优惠券状态不允许发放");
        }

        // 判断是否立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 更新优惠券
        Coupon c = BeanUtils.copyProperties(dto, Coupon.class);
        if (isBegin){ // 发放中
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        }else {// 未开始
            c.setStatus(CouponStatus.UN_ISSUE);
        }

        // 写入数据库
        updateById(c);

        // 添加缓存
        if(isBegin){
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 判断是否需要生成兑换码

        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    private void cacheCouponInfo(Coupon coupon) {
        // 1. 准备数据
        Map<String,String> map = new HashMap<>(4);
        map.put("issueBeginTime",String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime",String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum",String.valueOf(coupon.getTotalNum()));
        map.put("userLimit",String.valueOf(coupon.getUserLimit()));
        // 2. 写入缓存
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
    }


    /*
    暂定发放优惠券
     */



    @Override
    @Transactional
    public void pauseIssue(Long id) {
        //1.根据id查询优惠券
        Coupon coupon = getById(id);
        if(coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        //2.判断优惠券的状态 只有在发放中或者未开始的优惠券才能暂停
        if(coupon.getStatus() != CouponStatus.ISSUING
                && coupon.getStatus() != CouponStatus.UN_ISSUE){
            throw new BizIllegalException("该优惠券无法暂停");
        }
        //更改优惠券的状态
        boolean success = lambdaUpdate().eq(Coupon::getId, id)
                .set(Coupon::getStatus, CouponStatus.PAUSE)
                .in(Coupon::getStatus, CouponStatus.UN_ISSUE, CouponStatus.ISSUING)
                .update();
        if (!success){
            log.error("重复暂停优惠券");
        }


        // 4. 删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }




    /*
    用户查看发放中的优惠券
     */
    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1. 查询发放中优惠券列表
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        // 2. 统计当前用户已经领取的优惠券信息
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());  // 获取优惠券ID列表
        // 2.1 统计当前用户已经领取优惠券的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(UserCoupon::getCouponId, couponIds)
                .list();
        // 2.2 统计用户对优惠券的已经领取数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3 统计用户对优惠券的已经领取并且未使用的数量
        Map<Long, Long> iunusedMap = userCoupons.stream()
                .filter(userCoupon -> userCoupon.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3. 封装VO
        ArrayList<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            // 3.1 拷贝
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo);
            // 3.2 是否可以领取：已经被领取的数量 < 优惠券的总数 && 当前用户已经领取的数量＜用户的限领数量
            vo.setAvailable(c.getIssueNum() < c.getTotalNum()
                && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );
            // 3.3 是否可以使用：当前用户已经领取并且未使用的数量>0
            vo.setReceived(iunusedMap.getOrDefault(c.getId(), 0L) > 0);
        }
        return list;
    }
}

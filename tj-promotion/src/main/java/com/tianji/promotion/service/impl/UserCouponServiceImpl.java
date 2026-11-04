package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.SPELUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author author
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;


    /*
     * 领取优惠券
     */
    @Override
    @Lock(name = "lock:coupon：#{couponId}")
    public void receiveCoupon(Long couponId) {
        // 1. 查询优惠券
//        Coupon coupon = couponMapper.selectById(couponId);
        Coupon coupon = queryCouponCache(couponId);

        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 检验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getTermBeginTime()) || now.isAfter(coupon.getTermEndTime())) {
            throw new BadRequestException("该优惠券不在发放时间范围内");
        }
        // 3. 检验库存
        if (coupon.getIssueNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 检验并生成用户优惠券
        Long userId = UserContext.getUser();
//        synchronized (userId.toString().intern()) {
//            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//            userCouponService.checkAndCreateUserCoupon(coupon, userId);
//        }
//        String key = "lock:coupon:uid:" + userId;
//        RedisLock lock = new RedisLock(key, redisTemplate);
//        RLock lock = redissonClient.getLock(key);
//        boolean isLocked = lock.tryLock();
//        if (!isLocked) {
//            throw new BizIllegalException("请勿重复操作");
//        }
//        try {
//            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//            userCouponService.checkAndCreateUserCoupon(coupon, userId);
//        } finally {
//            lock.unlock();
//        }
        // 4. 检验每人的领取数量（一人一单问题）
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        // 4.1 查询领取数量
        Long count = redisTemplate.opsForHash().increment(
                PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1);
        // 4.2 检验领取数量
        if (count > coupon.getUserLimit()){
            throw new BadRequestException("已达到该优惠券的领取上限");
        }
        // 5. 扣减库存
        redisTemplate.opsForHash().increment(key, userId.toString(), -1);

        // 6. 发送mq消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,uc);


//        IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//        userCouponService.checkAndCreateUserCoupon(coupon, userId);
    }

    private Coupon queryCouponCache(Long couponId) {
        // 1. 准备数据
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2. 查询缓存
        Map<Object, Object> mapObj = redisTemplate.opsForHash().entries(key);
        if (mapObj.isEmpty()){
            return null;
        }
        // 3. 数据反序列化
        return BeanUtils.mapToBean(mapObj, Coupon.class, false, CopyOptions.create());
    }

    // 检验并生成用户优惠券
//        @Lock(name = "lock:coupon：#{userId}")
        @Transactional
        @Override
        public synchronized void checkAndCreateUserCoupon (UserCouponDTO uc){

            // 4. 检验每人领取数量
//            Integer count = lambdaQuery()
//                    .eq(UserCoupon::getUserId, userId)
//                    .eq(UserCoupon::getCouponId, coupon.getId())
//                    .count();
//            if (count != null && count >= coupon.getUserLimit()) {
//                throw new BadRequestException("已达到该优惠券的领取上限");
//            }

            // 查询优惠券
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null) {
                throw new BadRequestException("优惠券不存在");
            }
            // issueNum : 已发行数量
            // 5.更新优惠券的已经发放的数量+1,通过乐观锁来防止超卖
            int r = couponMapper.incrIssueNum(coupon.getId());
            if (r == 0) {
                throw new BizIllegalException("优惠券库存不足");
            }
            // 6. 新增一个用户券
            saveUserCoupon(coupon, uc.getUserId());

        }

    /*
    兑换码兑换优惠券
     */
        @Override
        @Transactional
        public void exchangeCoupon (String code){
            // 1. 解析兑换码,兑换码格式
            long serialNum = CodeUtil.parseCode(code);
            if (String.valueOf(serialNum).length() != 8) {
                throw new BadRequestException("兑换码格式错误");
            }
            // 2. 是否已经兑换
            boolean exchanged = codeService.updateExchangeMark(serialNum, true);
            if (exchanged) {
                throw new BizIllegalException("兑换码已经兑换过");
            }
            try {
                // 3. 查询兑换码
                ExchangeCode exchangeCode = codeService.getById(serialNum);
                if (exchangeCode == null) {
                    throw new BizIllegalException("兑换码不存在");
                }
                // 4. 是否过期
                if (LocalDateTime.now().isAfter(exchangeCode.getExpiredTime())) {
                    throw new BizIllegalException("兑换码已经过期");
                }
                // 5. 是否达到限领数量
                // 6. 更新已经发放的优惠券数量+1
                // 7. 新增一个用户券
                // 检验并生成用户优惠券
                Long userId = UserContext.getUser();
                Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
//                checkAndCreateUserCoupon(coupon, userId);

                // 8. 更新兑换码状态
                codeService.lambdaUpdate()
                        .set(ExchangeCode::getUserId, userId)
                        .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                        .eq(ExchangeCode::getId, serialNum)
                        .update();
            } catch (Exception e) {
                // 重置兑换码状态
                codeService.updateExchangeMark(serialNum, false);
                throw e;
            }

        }


    private void saveUserCoupon (Coupon coupon, Long userId){
            UserCoupon uc = new UserCoupon();
            uc.setUserId(userId);
            uc.setCouponId(coupon.getId());
            LocalDateTime termBeginTime = coupon.getTermBeginTime();
            LocalDateTime termEndTime = coupon.getTermEndTime();
            // 如果领取时没有设置有效期，则使用优惠券配置的固定有效期;
            if (termBeginTime == null) {
                termBeginTime = LocalDateTime.now();
                termEndTime = termBeginTime.plusDays(coupon.getTermDays());
            }
            uc.setTermBeginTime(termBeginTime);
            uc.setTermEndTime(termEndTime);
            save(uc);
        }
    }

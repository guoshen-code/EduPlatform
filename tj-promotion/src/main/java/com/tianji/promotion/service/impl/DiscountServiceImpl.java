package com.tianji.promotion.service.impl;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;

import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService scopeService;

    private final Executor discountSolutionExecutor;

    /*
根据课程订单列表查询优惠券优惠方案
 */
    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        // 1. 查询我的优惠券
        List<Coupon> coupons = userCouponMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2. 初步筛选
        // 2.1 计算订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        // 2.2 初筛，筛出可用券
        List<Coupon> availableCoupons = coupons.stream()
                .filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount, c))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) return CollUtils.emptyList();
        // 3. 排列所有优惠方案
        // 3.1 细筛，（找出每个优惠券的可用课程，判断课程总价是否达到优惠券的使用需求）
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, orderCourses);
        // 3.2 排列组合
        availableCoupons = new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 对于单张券的方案也添加到集合中
        for (Coupon c : availableCoupons) {
            solutions.add(List.of(c));
        }
        // 4. 计算每种方案
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        // 4.1 定义闭锁
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            // 4.2 异步计算
            CompletableFuture
                    .supplyAsync(() -> calculateSolutionDiscount(availableCouponMap, orderCourses, solution), discountSolutionExecutor)
                    .thenAccept(dto -> {
                        // 4.3 提交任务结果
                        list.add(dto);
                        latch.countDown();
                    });
        }
        // 4.4 等待运算结果
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("优惠方案计算被中断：{}", e.getMessage());
        }

        // 5. 筛选初最优解
        return findBestSolution(list);
    }

    // 筛选初最优解
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        // 1. 准备map记录最优解
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessDiscountMap = new HashMap<>();
        // 2. 遍历，找出最优解
        for (CouponDiscountDTO solution : list) {
            // 2.1 计算当前方案的id集合
            String ids = solution.getIds().stream()
                    .sorted(Long::compare).map(String::valueOf).collect(Collectors.joining(","));
            // 2.2 比较用券相同时，优惠金额是否最大
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()) {
                // 当前方案优惠较少，跳过
                continue;
            }
            // 2.3 比较金额相同时，用券最少
            best = lessDiscountMap.get(solution.getDiscountAmount());
            int size = solution.getIds().size();
            if (size > 1 && best != null && best.getIds().size() <= size) {
                // 当前方案用券更多，跳过
                continue;
            }
            // 2.4 更新最优解
            moreDiscountMap.put(ids, solution);
            lessDiscountMap.put(solution.getDiscountAmount(), solution);
        }
        // 计算交集
        Collection<CouponDiscountDTO> bestSolution = CollUtils
                .intersection(moreDiscountMap.values(), lessDiscountMap.values());
        // 排序，按照优惠金额排序
        return bestSolution.stream()
                .sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    private CouponDiscountDTO calculateSolutionDiscount(
            Map<Coupon, List<OrderCourseDTO>> couponMap,
            List<OrderCourseDTO> courses, List<Coupon> solution) {
        // 1. 初始化DTO
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2. 初始化折扣明细的映射
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        // 3. 计算折扣
        for (Coupon coupon : solution) {
            // 3.1 获取优惠券限定范围对应的课程
            List<OrderCourseDTO> availableCourses = couponMap.get(coupon);
            // 3.2 计算课程总结（课程原价-折扣明细）
            int totalAmount = availableCourses.stream().mapToInt(
                    oc -> oc.getPrice() - detailMap.get(oc.getId())).sum();
            // 3.3 判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)){
                continue;
            }
            // 4. 计算优惠金额
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 5. 计算优惠明细
            calculateDiscountDetails(detailMap,availableCourses,totalAmount,discountAmount);
            // 6. 更新dto数据
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
        }
        return dto;

    }

    private void calculateDiscountDetails(
            Map<Long, Integer> detailMap, List<OrderCourseDTO> courses, int totalAmount, int discountAmount) {
        // 优惠明细计算
        int times = 0;
        int remainDiscount = discountAmount;
        for (OrderCourseDTO course : courses) {
            // 更新课程已计算数量
            times++;
            int discount = 0;
            // 判断是否是最后一个课程
            if (times == courses.size()) {
                discount = remainDiscount;
            } else {
                // 计算优惠明细（课程价格在总价中的比例，乘以总的折扣）
                discount = discountAmount * course.getPrice() / totalAmount;
                remainDiscount -= discount;
            }
            // 更新折扣明细
            detailMap.put(course.getId(), detailMap.get(course.getId()) + discount);

        }

    }


    // 优惠券筛选
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(
            List<Coupon> coupons, List<OrderCourseDTO> courses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourses = courses;
            // 1. 找出优惠券的可用课程
            if (coupon.getSpecific()) {
                // 1.1 限定了范围，查询券的可用范围
                List<CouponScope> scopes = scopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                // 1.2 获取范围对应的分类id
                Set<Long> scopeIds = scopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                // 1.3 筛选课程
                availableCourses = courses.stream()
                        .filter(course -> scopeIds.contains(course.getCateId())).collect(Collectors.toList());
            }
            // 没有可用课程
            if (availableCourses.isEmpty()){
                continue;
            }
            // 2. 计算课程总价
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            // 3. 判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourses);
            }
        }
        return map;
    }
}

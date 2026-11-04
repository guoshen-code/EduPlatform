package com.tianji.promotion.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.domain.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/coupons")
@Api(tags = "优惠券管理相关接口")
public class CouponController {

    private final ICouponService couponService;
    @PostMapping
    @ApiOperation("新增优惠券")
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    /*
    分页查询优惠券吧
     */
    @GetMapping("/page")
    @ApiOperation("分页查询优惠券")
    public PageDTO<CouponPageVO> queryCouponByPage(@RequestBody CouponQuery query) {
        return couponService.queryCouponByPage(query);
    }

    /*
    发放优惠券
     */
    @PutMapping("{id}/issue")
    @ApiOperation("发放优惠券")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssue(dto);
    }



    @ApiOperation("根据id暂停发放优惠券")
    @PutMapping("/{id}/pause")
    public void pauseIssue(@PathVariable Long id) {
        couponService.pauseIssue(id);
    }
    @GetMapping("/list")
    @ApiOperation("用户查询正在发放中的优惠券列表")
    public List<CouponVO> queryIssuingCoupons() {
        return couponService.queryIssuingCoupons();
    }

}

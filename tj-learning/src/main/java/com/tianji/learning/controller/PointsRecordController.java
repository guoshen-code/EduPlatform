package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.PointsStatisticsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.domain.po.PointsRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/points")
@Api(tags = "积分相关接口")
public class PointsRecordController {

    private final IPointsRecordService pointsRecordService;

    /**
     * 获取今日积分
     */
    @GetMapping("/today")
    @ApiOperation("获取今日积分")
    public List<PointsStatisticsVO> queryMyPointsToday() {
        return pointsRecordService.queryMyPointsToday();
    }


}

package com.tianji.learning.service.impl;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {
    /**
     * 根据时间查询赛季id
     * @param lastMonth
     * @return
     */
    @Override
    public Integer querySeasonByTime(LocalDateTime lastMonth) {
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)
                .ge(PointsBoardSeason::getEndTime, lastMonth)
                .oneOpt();
        return optional.map(PointsBoardSeason::getId).orElse(null);
    }
}

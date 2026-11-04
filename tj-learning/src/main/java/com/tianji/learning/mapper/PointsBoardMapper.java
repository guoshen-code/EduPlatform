package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lettuce.core.dynamic.annotation.Param;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author author
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {

    void createPointsBoardTableOfLastSeason(@Param("tableName") String tableName);
}

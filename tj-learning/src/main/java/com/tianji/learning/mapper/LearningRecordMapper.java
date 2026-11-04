package com.tianji.learning.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import feign.Param;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author author
 */
/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author lusy
 * @since 2025-03-08
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    List<IdAndNumDTO> countLearnedSections(@Param("userId") Long userId, @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);
}
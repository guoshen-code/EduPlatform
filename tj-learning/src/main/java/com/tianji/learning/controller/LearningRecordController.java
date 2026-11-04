package com.tianji.learning.controller;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.ILearningRecordService;
import com.tianji.learning.domain.po.LearningRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学习记录表 控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/learning-records")
@Slf4j
@Api(tags = "学习记录的相关接口")
public class LearningRecordController {

    private final ILearningRecordService learningRecordService;



    @GetMapping("/course/{courseId}")
    @ApiOperation("查询课程的学习记录")
    public LearningLessonDTO queryLearningRecordByCourse(
            @ApiParam(value = "课程id",example = "2") @PathVariable("courseId") Long courseId){
        return learningRecordService.queryLearningRecordByCourse(courseId);

    }
    /*
    提交学习记录
     */
    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody LearningRecordFormDTO formDTO){
        learningRecordService.addLearningRecord(formDTO);
    }


}

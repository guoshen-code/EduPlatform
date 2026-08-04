package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.domain.po.LearningLesson;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 学生课程表 控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons")
@Slf4j
@Api(tags = "学生课程表接口")
public class LearningLessonController {
    private final ILearningLessonService learningLessonService;
    @GetMapping("/page")
    @ApiOperation("分页查询我的课程")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        log.info("查询我的课程，参数：{}", query);
        return learningLessonService.queryMyLessons(query);

    }
    @ApiOperation("查询我正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyNowLesson(){
        log.info("查询正在学习的课程");
        return learningLessonService.queryMyNowLesson();
    }


}

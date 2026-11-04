package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.domain.po.LearningLesson;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Collections;

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
    @ApiOperation("删除课程")
    @DeleteMapping("/{courseId}")
    public void deleteUserLessons(@PathVariable Long courseId) {

        Long userId = UserContext.getUser();

        learningLessonService.deleteUserLessons(userId, Collections.singletonList(courseId));
    }

    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return learningLessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryLessonByCourse(@PathVariable Long courseId) {
        return learningLessonService.queryLessonByCourse(courseId);
    }

    /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    @ApiOperation("统计课程学习人数")
    @GetMapping("/{courseId}/count")
    Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId) {
        return learningLessonService.countLearningLessonByCourse(courseId);
    }


    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        learningLessonService.createLearningPlan(planDTO.getCourseId(), planDTO.getFreq());
    }

    @ApiOperation("查询学习计划进度")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery){
        return learningLessonService.queryMyPlans(pageQuery);
    }







}

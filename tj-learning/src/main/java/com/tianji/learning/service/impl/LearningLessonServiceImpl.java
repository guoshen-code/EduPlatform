package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper learningRecordMapper;
    /*
    添加用户课表
     */
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 查询课程有限期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)){
            log.error("课程信息不存在");
            return;
        }
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 填充课程id和用户id
            lesson.setCourseId(cInfo.getId());
            lesson.setUserId(userId);
            list.add(lesson);
        }
        saveBatch(list);
        }
    /*
    分页查询我的课表
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        log.info("查询我的课程，参数：{}", query);
        // 获取当前用户id
        Long userId = UserContext.getUser();
        // 分页查询
        // select * from  learning_lesson where user_id = #{userId} order by latest_learn_time desc
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        // 3.查询课程信息
        // 3.1 获取课程id
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoMap(records);
        // 封装VO，返回
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoMap(List<LearningLesson> records) {
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2 查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)){
            throw new BadRequestException("课程信息不存在");
        }
        // 把课程集合处理成map,key是courseId，value是课程信息本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }

    /*
    查询正在学习的课程
     */
    @Override
    public LearningLessonVO queryMyNowLesson() {
        log.info("查询正在学习的课程");
        // 获取当前用户ID
        // 拿到当前用户的 id
        Long userId = UserContext.getUser();

        // 建立查询语句，条件 userId、status 为 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();

        // 如果查询结果为空，返回空
        if (lesson == null) {
            return null;
        }


        LearningLessonVO lessonVO = BeanUtil.copyProperties(lesson, LearningLessonVO.class);

        // 查看课程信息
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        lessonVO.setCourseName(courseInfo.getName());
        lessonVO.setCourseCoverUrl(courseInfo.getCoverUrl());
        lessonVO.setSections(courseInfo.getSectionNum());

        // 统计课表中的课程总数
        int count = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();

        lessonVO.setCourseAmount(count);

        // 查看目录信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(
                Collections.singletonList(lesson.getLatestSectionId()));

        if (cataSimpleInfoDTOS == null || cataSimpleInfoDTOS.isEmpty()) {
            throw new BizIllegalException("目录信息不存在");
        }

        CataSimpleInfoDTO cataInfo = cataSimpleInfoDTOS.get(0);
        lessonVO.setLatestSectionName(cataInfo.getName());
        lessonVO.setLatestSectionIndex(cataInfo.getCIndex());

        return lessonVO;
    }

    @Override
    public void deleteUserLessons(Long userId, List<Long> courseIds) {
        // 删除课程
        lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .remove();
    }

    @Override
    public Long isLessonValid(Long courseId) {

        // 获取用户 id
        Long user = UserContext.getUser();

        // 查看用户是否有这门课程的权限
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        if (lesson == null || lesson.getExpireTime() == null) {
            return null;
        }

        // 查看用户的课程有效期
        if (lesson.getStatus() == LessonStatus.EXPIRED || lesson.getExpireTime().isBefore(LocalDateTime.now())) {
            // 异步任务更新状态为 EXPIRED
            CompletableFuture.runAsync(() -> {
                if (lesson.getStatus() != LessonStatus.EXPIRED) {
                    lesson.setStatus(LessonStatus.EXPIRED);
                    updateById(lesson);
                }
            });
            return null;
        }

        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryLessonByCourse(Long courseId) {

        // 获取用户 id
        Long userId = UserContext.getUser();

        // 查询课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        return lesson == null ? null : BeanUtil.copyProperties(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN,
                        LessonStatus.LEARNING,
                        LessonStatus.FINISHED)
                .count();
    }
    /**
     * 创建学习计划
     * @param courseId 课程id
     * @param freq frequency
     */
    @Override
    public void createLearningPlan(Long courseId, Integer freq) {

        // 获取当前用户
        Long userId = UserContext.getUser();
        // 根据uerid和courseid查询课程信息
        LearningLesson learningLesson = queryByUserAndCourseId(userId, courseId);
        AssertUtils.isNotNull(learningLesson, "课程不存在");

        LearningLesson l = new LearningLesson();
        l.setId(learningLesson.getId());
        l.setWeekFreq(freq);
        if (learningLesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);

    }


    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {

        return lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
    }



    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {

        LearningPlanPageVO result = new LearningPlanPageVO();

        // 获取用户 id
        Long userId = UserContext.getUser();

        // 获取本周起始和结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);

        // 本周实际学习小节数量
        Integer weekFinished = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end));

        result.setWeekFinished(weekFinished);
        // 本周总的计划学习小节数量
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        // TODO 3.3.本周学习积分
        // 4.查询分页数据
        // 4.1.分页查询课表信息以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = p.getRecords();
        // 判断是否为空值
        if (CollUtil.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtil.newArrayList());
            return vo;
        }


        // 4.2.查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3.统计每一个课程本周已学习小节数量
        List<IdAndNumDTO> list = learningRecordMapper.countLearnedSections(userId, begin, end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        // 封装数据vo
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            // 填入课程信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo != null){
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }


            // 本周已学习小节数量
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(), 0));
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(), p.getPages(), voList);

    }


    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 获取课程 id 集合
        List<Long> courseIds = records.stream()
                .map(LearningLesson::getCourseId)
                .collect(Collectors.toList());

        // 根据课程 id 集合查询课程简单信息
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOList = courseClient.getSimpleInfoList(courseIds);

        if (CollUtils.isEmpty(courseSimpleInfoDTOList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }

        Map<Long, CourseSimpleInfoDTO> cInfoDTOMap = courseSimpleInfoDTOList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cInfoDTOMap;
    }
}


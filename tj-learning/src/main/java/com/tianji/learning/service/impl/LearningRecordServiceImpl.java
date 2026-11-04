package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    private final ILearningLessonService learningLessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;
    /*
     * 查询用户在某个课程的学习记录
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1. 查询用户id
        Long userId = UserContext.getUser();
        // 2. 查询课表
        LearningLesson lesson = learningLessonService.queryByUserAndCourseId(userId, courseId);
        // 3. 查询学习记录
        List<LearningRecord> records = lambdaQuery().eq(LearningRecord::getUserId, userId)
                .list();

        // 4.封装结果
        LearningLessonDTO lessonDTO = new LearningLessonDTO();
        lessonDTO.setId(lesson.getId());
        lessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        lessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return lessonDTO;

    }
    /*
     提交学习记录
      */
    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        // 1. 获取登录用户
        Long userId = UserContext.getUser();
        // 是否课程完成
        boolean finished = false;
        if (recordDTO.getSectionType() == SectionType.VIDEO) {
            // 处理视频
            finished = handleVideoRecord(userId, recordDTO);
        }else {
            // 处理考试
            finished = handleExamRecord(userId, recordDTO);

        }
        // 没有学完的小节，无需更新课表中的学习进度
        if(!finished){
            return;
        }
        // 有学完的小节
        handleLearningLessonsChanges(recordDTO);


    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO recordDTO) {
        // 1. 查询课表
        LearningLesson lesson = learningLessonService.getById(recordDTO.getLessonId());
        if (lesson == null) {
            throw new DbException("课表不存在,无法更新数据");
        }

        // 判断是否有新的小节学完
        boolean allLearned = false;

        // 如果有新的小节完成，需要查询课程数据
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo ==  null){
            throw new DbException("课程不存在,无法更新数据");
        }
        // 比较课程是否全部学完
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();



        learningLessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned,LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("latest_section = latest_section + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {

        // 判断是否存在旧数据
        LearningRecord old = queryOldRecord(recordDTO.getLessonId(),recordDTO.getSectionId());
        if (old == null) {
            // 不存在，新增数据
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            record.setUserId(userId);
            boolean success = save(record);
            if (!success){
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        // 存在，更新数据
        // 判断视频是否一次性完成
        boolean finished = !old.getFinished() && recordDTO.getMoment()*2 > recordDTO.getDuration();
        // 如果不是第一次完成
        if (!finished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        // 是第一次看完视频，更新数据
        boolean success = lambdaUpdate().set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!success){
            throw new DbException("更新学习记录失败");
        }
        // 删除redis缓存
        taskHandler.cleanRecordCache(recordDTO.getLessonId(), recordDTO.getSectionId());
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1. 查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        // 2. 缓存命中，直接返回
        if (record != null) {
            return record;
        }
        // 3. 缓存未命中，查询数据库
        record = lambdaQuery().eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4. 更新缓存
        taskHandler.writeRecordCache(record);
        return record;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        boolean success = save(record);
        if (!success){
            throw new DbException("新增考试记录失败");
        }
        return true;
    }


}

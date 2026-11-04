package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {
    private final StringRedisTemplate redisTemplate;

    private final LearningRecordMapper recordMapper;

    private final ILearningLessonService lessonService;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";

    private static volatile boolean begin = true;

    // 处理延迟任务
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    @PreDestroy
    public void destroy() {
        begin = false;
        log.debug("延迟任务停止执行");
    }

    public void handleDelayTask() {

        while (begin) {
            try {
                // 1. 获取延迟任务
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                // 2. 获取redis的缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null) {
                    continue;
                }
                // 3. 比较数据，moment值
                // 4. 不一致，说明用户还在持续提交播放进度，放弃旧数据
                if (!data.getMoment().equals(record.getMoment())) {
                    continue;
                }
                // 5. 一致，持久化播放进度到数据库
                // 5.1 更新学习记录moment
                record.setFinished(null);
                recordMapper.updateById(record);
                // 5.2 更新课表最近信息
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);
                // 5. 一致，持久化播放进度到数据库
            } catch (Exception e) {
                log.error("处理延迟任务发生异常", e);
            }
        }
    }
    // 添加学习记录到缓存，检测延迟任务
    public void addLearningRecordTask(LearningRecord record) {
        // 1. 添加数据到缓存
        writeRecordCache(record);
        // 2. 提交延迟检测任务
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    // 将学习记录数据写入缓存
    public void writeRecordCache(LearningRecord record) {
        log.debug("Writing record cache for lesson: {}", record.getLessonId());
        try {
            // 1. 数据转化
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2. 写入redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3. 设置过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("Failed to write record cache for lesson: {}", record.getLessonId(), e);
        }
    }

    // 从缓存中查询学习记录数据

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("Failed to read record cache for lesson: {} and section: {}", lessonId, sectionId, e);
            return null;
        }
    }

    // 从缓存中删除学习记录数据
    public void cleanRecordCache(Long lessonId, Long sectionId) {
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    public static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    public static class RecordTaskData {

        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }


    }
}

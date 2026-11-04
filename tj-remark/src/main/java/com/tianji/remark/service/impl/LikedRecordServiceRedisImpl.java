package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import com.tianji.remark.constants.RedisConstants;

import java.lang.invoke.StringConcatException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;
    /*
     * 点赞或者取消记录
     */
    @Override
    public void addLikeRecord(LikeRecordFormDTO recordTO) {
        // 1. 基于前端的数据进行判断，是点赞还是取消点赞
        boolean success = recordTO.getLiked() ? like(recordTO) : unlike(recordTO);

        // 2. 判断是否执行成功，如果失败，直接结束
        if (!success) {
            return;
        }
        // 3. 执行点赞或者取消点赞
        Long likeTimes = redisTemplate.opsForSet()
                .size(RedisConstants.LIKES_BIZ_KEY_PREFIX + recordTO.getBizId());
        if (likeTimes == null){
            return;
        }
        // 4. 发送MQ通知
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordTO.getBizType(),
                recordTO.getBizId().toString(),
                likeTimes
        );
    }
    /*
     * 查询指定业务id的点赞状态
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long userId = UserContext.getUser();
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 返回结果
//        Set<Long> set = new HashSet<>();
//        for (int i = 0; i < objects.size(); i++) {
//            Boolean o = (Boolean) objects.get(i);
//            if (o){
//                set.add(bizIds.get(i));
//            }
//        }
        return IntStream.range(0, objects.size())
                .filter(i -> (Boolean) objects.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1. 从redis读取并删除点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        // 数据转换
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likeTimes = tuple.getScore();
            if (bizId == null || likeTimes == null){
                continue;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likeTimes.intValue()));
        }
        // 3. 发送mq
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list
        );
    }

    private boolean unlike(LikeRecordFormDTO recordTO) {
        Long userId = UserContext.getUser();
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordTO.getBizId();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO recordTO) {
        Long userId = UserContext.getUser();
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordTO.getBizId();
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }
}

package com.tianji.remark.service.impl;

import ch.qos.logback.classic.spi.EventArgUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    private final RabbitMqHelper mqHelper;
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
        Integer likeTimes = lambdaQuery()
                .eq(LikedRecord::getBizId, recordTO.getBizId())
                .count();

        // 4. 发送MQ通知
        mqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, recordTO.getBizId()),
                LikedTimesDTO.of(recordTO.getBizId(), likeTimes));
    }
    /*
     * 查询指定业务id的点赞状态
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long userId = UserContext.getUser();
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {

    }

    private boolean unlike(LikeRecordFormDTO recordTO) {
        Long userId = UserContext.getUser();
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordTO.getBizId()));

    }

    private boolean like(LikeRecordFormDTO recordTO) {
        Long userId = UserContext.getUser();
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordTO.getBizId())
                .count();
        if (count > 0) {
            return true;
        }
        LikedRecord r = new LikedRecord();
        r.setUserId(userId);
        r.setBizId(recordTO.getBizId());
        r.setBizType(recordTO.getBizType());
        save(r);
        return true;
    }
}

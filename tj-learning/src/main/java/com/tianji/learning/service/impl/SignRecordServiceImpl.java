package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    /*
     * 签到功能
     */
    @Override
    public SignResultVO addSignRecords() {
        // 1. 签到
        // 1.1 获取当前登录用户
        Long userId = UserContext.getUser();
        // 1.2 获取日期
        LocalDate now = LocalDate.now();
        // 1.3 拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 1.4 计算offset
        int offset = now.getDayOfMonth() - 1;
        // 1.5 保存签到信息
        Boolean exist = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exist)) {
            throw new BizIllegalException("不允许重复签到");
        }

        // 2. 计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());


        // 3. 计算签到积分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 5;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }

        // 4. 保存积分明细记录
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignMessage.of(userId, rewardPoints + 1));

        // 5. 封装VO


        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    /*
    计算连续签到天数
     */
    private int countSignDays(String key, int len) {
        // 1. 获取从本月开始到今天的签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(
                BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        // 2. 定义计数器
        int count = 0;
        // 拿到签到数据
        int num = result.get(0).intValue();
        // 3. 与1做与运算
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return count;
    }
}

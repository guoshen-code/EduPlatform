package com.tianji.aigc.memory.jdbc;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.aigc.entity.ChatRecord;
import com.tianji.aigc.memory.MessageUtil;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import com.tianji.aigc.service.ChatRecordService;
import com.tianji.aigc.service.ChatService;
import com.tianji.common.utils.Convert;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
public class JdbcChatMemoryRepository implements ChatMemoryRepository , MyChatMemoryRepository {
    @Resource
    private ChatRecordService chatRecordService;
    @Override
    public List<String> findConversationIds() {
        List<ChatRecord> chatRecordList = chatRecordService.lambdaQuery()
                .select(ChatRecord::getConversationId)
                .list();
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<ChatRecord> chatRecordList = chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByAsc(ChatRecord::getCreateTime)
                .list();
        return CollStreamUtil.toList(chatRecordList, chatRecord -> MessageUtil.toMessage(chatRecord.getData()));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {

        // 先删除原有数据
        deleteByConversationId(conversationId);

        // 通过对话id获取userID
        Long userId = Long.valueOf(StrUtil.subBefore(conversationId, "_", true));
        // 批量插入
        List<ChatRecord> chatRecords = CollStreamUtil.toList(messages, message -> ChatRecord.builder()
                .conversationId(conversationId)
                .data(MessageUtil.toJson(message))
                .creater(userId)
                .updater(userId)
                .build());
        chatRecordService.saveBatch(chatRecords);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatRecord> queryWrapper = Wrappers.<ChatRecord>lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId);
        chatRecordService.remove(queryWrapper);

    }

    @Override
    public void optimization(String conversationId) {
        // TODO
    }
}

package com.tianji.aigc.memory.mongodb;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import com.tianji.aigc.memory.mongodb.ChatRecord;
import com.tianji.aigc.memory.MessageUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

public class MongoDBChatMemoryRepository implements ChatMemoryRepository , MyChatMemoryRepository {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public List<String> findConversationIds() {
        List<ChatRecord> chatRecordList = mongoTemplate.findAll(ChatRecord.class);
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        ChatRecord chatRecord = mongoTemplate.findOne(query, ChatRecord.class);
        if (chatRecord == null || CollUtil.isEmpty(chatRecord.getMessages())) {
            return List.of();
        }
        return CollStreamUtil.toList(chatRecord.getMessages(), MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 先删除原有数据
        deleteByConversationId(conversationId);

        // 一个对话保存为一个文档，messages 中存 JSON 字符串列表
        ChatRecord chatRecord = ChatRecord.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(messages, MessageUtil::toJson))
                .build();
        mongoTemplate.save(chatRecord);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId));
        mongoTemplate.remove(query, ChatRecord.class);
    }

    @Override
    public void optimization(String conversationId) {
        // TODO

    }
}

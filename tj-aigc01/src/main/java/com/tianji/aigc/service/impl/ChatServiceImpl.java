package com.tianji.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ENHANCE")
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVEBT = ChatEventVO.builder()  // 标记输出结束
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();
    private final ChatClient chatClient;

    private final ChatClient openAiChatClient;

    private final SystemPromptConfig systemPromptConfig;
    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    // 临时存储生成的状态
//    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    private final StringRedisTemplate stringRedisTemplate;

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";
    // 存储大模型的生成状态，这里采用Redis实现，确保分布式环境下的线程安全
    private final ChatMemory chatMemory;

    private final VectorStore vectorStore;

    private final ChatSessionService chatSessionService;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 将会话id转为对话id
        String conversationId = ChatService.getConversationId(sessionId);
        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        var outputBuilder = new StringBuilder();
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        String requestId = IdUtil.simpleUUID();

        // 获取用户id
        Long userId = UserContext.getUser();
        // 定义RAG增强
        var qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.6d)
                        .topK(10)
                        .build())
                .build();
        // 异步更新会话标题和时间
        chatSessionService.update(sessionId, question, userId);
        return this.chatClient.prompt()
                // 获取配置文件中的系统提示词
                .system(promptSystemSpec -> promptSystemSpec.text(this.systemPromptConfig.getChatSystemMessage().get())
                        .params(Map.of("now", DateUtil.now())))  // 系统提示词中的时间参数
                .advisors(advisor -> advisor
                        .advisors(qaAdvisor) // 添加RAG增强
                        .param(ChatMemory.CONVERSATION_ID,conversationId)) // 设置对话记忆中的对话id
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId)) // 向工具中传递参数
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true")) // 当生成开始时，将状态设为true
                .doOnError(throwable -> hashOps.delete(sessionId)) // 当生成发生错误时，将状态移除
                .doOnComplete(() -> hashOps.delete(sessionId))// 当生成完成时，将状态移除
                .doOnCancel(() -> this.saveStopHistoryRecord(conversationId, outputBuilder.toString())) // 当生成取消时，保存停止输出的记录
                .takeWhile(response -> hashOps.get(sessionId) != null) // 后续生成条件，false：停止生成
                .map(chatResponse -> {
                    // 获取大模型的输出的内容
                    String text = chatResponse.getResult().getOutput().getText();
                    // 追加到输出的内容中
                    outputBuilder.append(text);
                    String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if (StrUtil.equals(finishReason, Constant.STOP)) {
                        // 获取消息id
                        String messageId = chatResponse.getMetadata().getId();
                        // 将消息id与请求id进行关联
                        ToolResultHolder.put(messageId,Constant.REQUEST_ID,requestId);
                    }
                    // 封装响应对象
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    var result = ToolResultHolder.get(requestId);
                    if (ObjectUtil.isNotEmpty(result)) {
                        ToolResultHolder.remove(requestId);
                        // 工具被调用，向前端返回参数
                        return Flux.just(ChatEventVO.builder()
                                        .eventData(result)
                                        .eventType(ChatEventTypeEnum.PARAM.getValue())
                                        .build(),
                                STOP_EVEBT
                        );
                    }
                    return Flux.just(STOP_EVEBT);
        }));
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        chatMemory.add(conversationId, new AssistantMessage(content));
    }
    /*
     * 停止生成
     */
    @Override
    public void stop(String sessionId) {
        log.info("stop generate sessionId: {}", sessionId);
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }

    @Override
    public String chatText(String question) {
        return openAiChatClient.prompt()
                .system(systemPromptConfig.getChatSystemMessage().get())
                .user(question)
                .call()
                .content();
    }
}

package com.tianji.aigc.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractAgent implements Agent {

    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private ChatClient chatClient;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 输出结束的标记
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现


    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";
    public static final ChatEventVO STOP_EVEBT = ChatEventVO.builder()  // 标记输出结束
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();

    @Override
    public String process(String question, String sessionId) {
        // 生成请求id
        String requestId = generateRequestId();
        // 获取用户id
        Long userId = UserContext.getUser();
        // 异步更新会话标题和时间
        chatSessionService.update(sessionId, question, userId);
        return getChatClientRequest(question, sessionId, requestId)
                .call()
                .content();
    }



    public Flux<ChatEventVO> processStream(String question, String sessionId) {
        // 将会话id转为对话id
        String conversationId = ChatService.getConversationId(sessionId);
        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        var outputBuilder = new StringBuilder();
        // 生成请求id
        String requestId = generateRequestId();
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);

        // 获取用户id
        Long userId = UserContext.getUser();
        // 异步更新会话标题和时间
        chatSessionService.update(sessionId, question, userId);

        return getChatClientRequest(question, sessionId, requestId)
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
    /*
     * 获取ChatClientRequest
     */
    @NotNull
    private ChatClient.ChatClientRequestSpec getChatClientRequest(String question, String sessionId, String requestId) {
        return chatClient.prompt()
                .system(promptSystemSpec -> promptSystemSpec.text(this.systemMessage()).params(this.systemMessageParams()))
                .advisors(advisorSpec -> advisorSpec.advisors(this.advisors()).params(this.advisorParams(sessionId, requestId)))
                .tools(this.tools()) //
                .toolContext(this.toolContext(sessionId, requestId))
                .user(question);
    }
    /**
     * 保存停止输出的记录
     *
     * @param conversationId 对话id
     * @param content   大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        chatMemory.add(conversationId, new AssistantMessage(content));
    }

    private String generateRequestId() {
        return IdUtil.fastSimpleUUID();
    }

    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId) {
        return Map.of(ChatMemory.CONVERSATION_ID,ChatService.getConversationId(sessionId));
    }

    @Override
    public void stop(String sessionId) {
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }
}

package com.tianji.aigc.advisor;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;

public class RecordOptimizationAdvisor2 implements BaseAdvisor {

    public static final String OPTIMIZATION = "optimization";
    private final MyChatMemoryRepository myChatMemoryRepository;
    public RecordOptimizationAdvisor2(MyChatMemoryRepository myChatMemoryRepository) {
        this.myChatMemoryRepository = myChatMemoryRepository;
    }
    // 对于Recommendation智能体的输入删除，保留输出
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        UserMessage userMessage = chatClientRequest.prompt().getUserMessage();
        // 标记是否需要被优化，在after中进行判断
        //// 检查当前用户消息中是否包含“路由指令”（比如以 @RouteAgent 开头）,如果是，往上下文中放一个 true 标记
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(userMessage.getText());
        chatClientRequest.context().put(OPTIMIZATION, agentTypeEnum != null);
        return chatClientRequest;
    }
    /*
     * 后置处理:由于路由智能体的响应内容中会包含智能体类型，所以要删除
     * 对于路由智能体，删除输出中的智能体类型，保留输入
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 获取大模型的响应
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        // 判断响应内容
        String text = chatResponse.getResult().getOutput().getText();
        AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(text);
        var context = chatClientResponse.context();
        // 获取对话id
        String conversationId = MapUtil.getStr(context, "conversationId");
        if (agentType != null){
            // 优化
            this.myChatMemoryRepository.optimization(conversationId);
        }
        if (MapUtil.getBool(context, OPTIMIZATION,false)){
            // 输入给大模型的内容为智能体内容，需要优化数据，并且是在最后进行数据处理
            String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
            if (StrUtil.equals(finishReason, Constant.STOP)){
                this.myChatMemoryRepository.optimization(conversationId);
            }
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }
}

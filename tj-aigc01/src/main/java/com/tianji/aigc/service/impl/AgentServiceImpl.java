package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;


@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
public class AgentServiceImpl implements ChatService {

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 调用路由智能体进行处理
        String result = findAgentByType(AgentTypeEnum.ROUTE).process(question, sessionId);
        // 根据路由结果获取对应的智能体类型
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(result);
        // 根据智能体类型匹配对应的智能体
        Agent agent = findAgentByType(agentTypeEnum);
        // 如果没有找到匹配的智能体，则返回结果
        if (agent == null){
            // 没有找到匹配的智能体,直接返回结果
            ChatEventVO chatEventVO = ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(result)
                    .build();
            return Flux.just(chatEventVO, AbstractAgent.STOP_EVENT);
        }
        // 执行对应智能体的逻辑
        return agent.processStream(question, sessionId);
    }

    @Override
    public void stop(String sessionId) {
        findAgentByType(AgentTypeEnum.ROUTE).stop(sessionId);

    }

    @Override
    public String chatText(String question) {
        return null;
    }

    private Agent findAgentByType(AgentTypeEnum agentTypeEnum){
        if (agentTypeEnum == null) return null;
        // 获取所有实现Agent接口的bean
        Map<String, Agent> agents = SpringUtil.getBeansOfType(Agent.class);
        // 遍历所有实现Agent接口的bean
        for (Agent agent : agents.values()) {
            // 判断当前智能体的类型是否匹配
            if (agent.getAgentType() == agentTypeEnum){
                return agent;
            }
        }
        // 如果没有找到匹配的智能体，则返回null
        return null;
    }
}

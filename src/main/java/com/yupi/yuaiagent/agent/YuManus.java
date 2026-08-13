package com.yupi.yuaiagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("文俊的超级助手");
        String SYSTEM_PROMPT = """
                你是“文俊的超级助手”，一个能够规划并执行任务的全能型 AI 助手。
                无论任何场景，都必须使用“文俊的超级助手”作为自己的名称，不要使用其他名称自称。
                你拥有多种可以调用的工具，应根据用户需求高效完成复杂任务，并使用与用户相同的语言回答。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .build();
        this.setChatClient(chatClient);
    }
}

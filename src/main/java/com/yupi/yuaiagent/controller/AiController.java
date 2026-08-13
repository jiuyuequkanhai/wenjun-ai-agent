package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.IndustryResearchApp;
import com.yupi.yuaiagent.service.IndustryDocumentService;
import com.yupi.yuaiagent.service.ConversationHistoryService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private IndustryResearchApp industryResearchApp;

    @Resource
    private IndustryDocumentService industryDocumentService;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 旧版同步接口，保留用于兼容原有调用方。
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return industryResearchApp.doChat(message, chatId);
    }

    /**
     * 旧版流式接口，保留用于兼容原有调用方。
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return industryResearchApp.doChatByStream(message, chatId);
    }

    /**
     * POST 流式调用行业调研助手。
     * POST 请求可以承载长篇原始资料，不受浏览器 URL 长度限制。
     */
    @PostMapping(
            value = "/industry_research/chat/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> doChatWithIndustryResearch(@RequestBody IndustryResearchRequest request) {
        if (request == null || ((request.message() == null || request.message().isBlank())
                && (request.documentIds() == null || request.documentIds().isEmpty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原始资料或消息不能为空");
        }
        if (request.chatId() == null || request.chatId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话 ID 不能为空");
        }
        String prompt;
        try {
            prompt = industryDocumentService.buildPromptWithDocuments(
                    request.message(), request.chatId(), request.documentIds()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        List<ConversationHistoryService.ContextMessage> history = conversationHistoryService.getRecentMessages(
                ConversationHistoryService.INDUSTRY_RESEARCH, request.chatId()
        );
        conversationHistoryService.appendUserMessage(
                ConversationHistoryService.INDUSTRY_RESEARCH,
                request.chatId(),
                request.message(),
                prompt
        );
        String promptWithHistory = conversationHistoryService.buildPromptWithHistory(prompt, history);
        StringBuilder answer = new StringBuilder();
        return industryResearchApp.doChatByStream(promptWithHistory)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(answer::append)
                .doOnComplete(() -> conversationHistoryService.appendAssistantMessage(
                        ConversationHistoryService.INDUSTRY_RESEARCH, request.chatId(), answer.toString()
                ))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWithValues(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build());
    }

    /**
     * 上传并读取 PDF、DOC 或 DOCX 行业资料。
     */
    @PostMapping(
            value = "/industry_research/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<IndustryDocumentService.UploadedDocument> uploadIndustryDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("chatId") String chatId
    ) {
        try {
            return industryDocumentService.uploadDocuments(files, chatId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 从当前会话中移除误上传的文档。
     */
    @DeleteMapping("/industry_research/files/{documentId}")
    public void deleteIndustryDocument(
            @PathVariable String documentId,
            @RequestParam("chatId") String chatId
    ) {
        try {
            industryDocumentService.deleteDocument(documentId, chatId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 旧版 ServerSentEvent 接口，保留用于兼容原有调用方。
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return industryResearchApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 旧版 SseEmitter 接口，保留用于兼容原有调用方。
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        industryResearchApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息不能为空");
        }
        if (chatId == null || !chatId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话 ID 无效");
        }
        List<ConversationHistoryService.ContextMessage> history = conversationHistoryService.getRecentMessages(
                ConversationHistoryService.SUPER_AGENT, chatId
        );
        conversationHistoryService.appendUserMessage(
                ConversationHistoryService.SUPER_AGENT, chatId, message, message
        );
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        List<Message> agentHistory = history.stream()
                .map(item -> "user".equals(item.role())
                        ? (Message) new UserMessage(item.content())
                        : new AssistantMessage(item.content()))
                .toList();
        yuManus.setMessageList(new java.util.ArrayList<>(agentHistory));
        return yuManus.runStream(message, answer -> conversationHistoryService.appendAssistantMessage(
                ConversationHistoryService.SUPER_AGENT, chatId, answer
        ));
    }

    public record IndustryResearchRequest(String message, String chatId, List<String> documentIds) {
    }
}

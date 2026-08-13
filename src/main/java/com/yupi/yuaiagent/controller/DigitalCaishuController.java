package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.app.DigitalCaishuApp;
import com.yupi.yuaiagent.service.CaishuKnowledgeService;
import com.yupi.yuaiagent.service.ConversationHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/ai/caishu")
public class DigitalCaishuController {

    private final CaishuKnowledgeService knowledgeService;
    private final DigitalCaishuApp digitalCaishuApp;
    private final ConversationHistoryService conversationHistoryService;

    public DigitalCaishuController(
            CaishuKnowledgeService knowledgeService,
            DigitalCaishuApp digitalCaishuApp,
            ConversationHistoryService conversationHistoryService
    ) {
        this.knowledgeService = knowledgeService;
        this.digitalCaishuApp = digitalCaishuApp;
        this.conversationHistoryService = conversationHistoryService;
    }

    @GetMapping("/status")
    public CaishuKnowledgeService.IndexStatus getStatus() {
        return knowledgeService.getStatus();
    }

    @PostMapping("/reindex")
    public CaishuKnowledgeService.IndexStatus reindex() {
        return knowledgeService.startIndexing();
    }

    @PostMapping(
            value = "/chat/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }
        if (request.chatId() == null || !request.chatId().matches("[A-Za-z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话 ID 无效");
        }

        List<ConversationHistoryService.ContextMessage> history = conversationHistoryService.getRecentMessages(
                ConversationHistoryService.DIGITAL_CAISHU, request.chatId()
        );
        String searchQuestion = conversationHistoryService.contextualizeSearchQuery(request.message(), history);
        List<CaishuKnowledgeService.SearchResult> sources;
        try {
            sources = knowledgeService.search(searchQuestion);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        conversationHistoryService.appendUserMessage(
                ConversationHistoryService.DIGITAL_CAISHU,
                request.chatId(),
                request.message(),
                request.message()
        );
        StringBuilder answer = new StringBuilder();
        return digitalCaishuApp.answerByStream(request.message(), sources, history)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(answer::append)
                .doOnComplete(() -> conversationHistoryService.appendAssistantMessage(
                        ConversationHistoryService.DIGITAL_CAISHU, request.chatId(), answer.toString()
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

    public record ChatRequest(String message, String chatId) {
    }
}

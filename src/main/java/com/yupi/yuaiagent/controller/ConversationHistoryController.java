package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.service.ConversationHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/ai/conversations")
public class ConversationHistoryController {

    private final ConversationHistoryService conversationHistoryService;

    public ConversationHistoryController(ConversationHistoryService conversationHistoryService) {
        this.conversationHistoryService = conversationHistoryService;
    }

    @GetMapping("/{agentType}")
    public List<ConversationHistoryService.ConversationSummary> list(@PathVariable String agentType) {
        try {
            return conversationHistoryService.listConversations(agentType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{agentType}/{conversationId}")
    public ConversationHistoryService.ConversationDetail get(
            @PathVariable String agentType,
            @PathVariable String conversationId
    ) {
        try {
            return conversationHistoryService.getConversation(agentType, conversationId);
        } catch (ConversationHistoryService.ConversationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{agentType}/{conversationId}")
    public void delete(@PathVariable String agentType, @PathVariable String conversationId) {
        try {
            if (!conversationHistoryService.deleteConversation(agentType, conversationId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到需要删除的会话");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}

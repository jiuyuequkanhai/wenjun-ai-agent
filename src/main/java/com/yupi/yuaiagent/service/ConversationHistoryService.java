package com.yupi.yuaiagent.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 三个 AI 应用共用的本地会话历史服务，按智能体类型隔离保存。
 */
@Service
public class ConversationHistoryService {

    public static final String INDUSTRY_RESEARCH = "industry-research";
    public static final String SUPER_AGENT = "super-agent";
    public static final String DIGITAL_CAISHU = "digital-caishu";

    private static final Set<String> SUPPORTED_AGENT_TYPES =
            Set.of(INDUSTRY_RESEARCH, SUPER_AGENT, DIGITAL_CAISHU);
    private static final int TITLE_MAX_LENGTH = 32;
    private static final int HISTORY_MESSAGE_LIMIT = 24;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.public-demo.enabled:false}")
    private boolean publicDemoEnabled;

    public ConversationHistoryService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_conversations (
                    agent_type VARCHAR(40) NOT NULL,
                    id VARCHAR(100) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (agent_type, id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_conversation_messages (
                    id BIGSERIAL PRIMARY KEY,
                    agent_type VARCHAR(40) NOT NULL,
                    conversation_id VARCHAR(100) NOT NULL,
                    role VARCHAR(16) NOT NULL CHECK (role IN ('user', 'assistant')),
                    display_content TEXT NOT NULL,
                    model_content TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (agent_type, conversation_id)
                        REFERENCES ai_conversations(agent_type, id) ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ai_conversations_agent_updated_idx
                ON ai_conversations (agent_type, updated_at DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ai_messages_conversation_idx
                ON ai_conversation_messages (agent_type, conversation_id, id)
                """);
    }

    public List<ConversationSummary> listConversations(String agentType) {
        validateAgentType(agentType);
        String storageAgentType = storageAgentType(agentType);
        return jdbcTemplate.query("""
                SELECT c.id, c.title, c.created_at, c.updated_at,
                       (SELECT COUNT(*) FROM ai_conversation_messages m
                        WHERE m.agent_type = c.agent_type AND m.conversation_id = c.id) AS message_count,
                       COALESCE((
                           SELECT LEFT(m.display_content, 100)
                           FROM ai_conversation_messages m
                           WHERE m.agent_type = c.agent_type AND m.conversation_id = c.id
                           ORDER BY m.id DESC
                           LIMIT 1
                       ), '') AS last_message
                FROM ai_conversations c
                WHERE c.agent_type = ?
                ORDER BY c.updated_at DESC
                """, (resultSet, rowNumber) -> mapSummary(resultSet), storageAgentType);
    }

    public ConversationDetail getConversation(String agentType, String conversationId) {
        validate(agentType, conversationId);
        String storageAgentType = storageAgentType(agentType);
        List<ConversationSummary> summaries = jdbcTemplate.query("""
                SELECT c.id, c.title, c.created_at, c.updated_at,
                       (SELECT COUNT(*) FROM ai_conversation_messages m
                        WHERE m.agent_type = c.agent_type AND m.conversation_id = c.id) AS message_count,
                       COALESCE((
                           SELECT LEFT(m.display_content, 100)
                           FROM ai_conversation_messages m
                           WHERE m.agent_type = c.agent_type AND m.conversation_id = c.id
                           ORDER BY m.id DESC
                           LIMIT 1
                       ), '') AS last_message
                FROM ai_conversations c
                WHERE c.agent_type = ? AND c.id = ?
                """, (resultSet, rowNumber) -> mapSummary(resultSet), storageAgentType, conversationId);
        if (summaries.isEmpty()) {
            throw new ConversationNotFoundException(conversationId);
        }
        return new ConversationDetail(summaries.getFirst(), getDisplayMessages(storageAgentType, conversationId));
    }

    public List<ContextMessage> getRecentMessages(String agentType, String conversationId) {
        validate(agentType, conversationId);
        String storageAgentType = storageAgentType(agentType);
        return jdbcTemplate.query("""
                SELECT role, model_content
                FROM (
                    SELECT id, role, model_content
                    FROM ai_conversation_messages
                    WHERE agent_type = ? AND conversation_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                ) recent
                ORDER BY id
                """, (resultSet, rowNumber) -> new ContextMessage(
                resultSet.getString("role"),
                resultSet.getString("model_content")
        ), storageAgentType, conversationId, HISTORY_MESSAGE_LIMIT);
    }

    public void appendUserMessage(
            String agentType,
            String conversationId,
            String displayContent,
            String modelContent
    ) {
        validate(agentType, conversationId);
        String storageAgentType = storageAgentType(agentType);
        String display = normalize(displayContent, "已提交资料");
        String model = normalize(modelContent, display);
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            jdbcTemplate.update("""
                    INSERT INTO ai_conversations (agent_type, id, title)
                    VALUES (?, ?, ?)
                    ON CONFLICT (agent_type, id) DO NOTHING
                    """, storageAgentType, conversationId, titleFrom(display));
            insertMessage(storageAgentType, conversationId, "user", display, model);
            touch(storageAgentType, conversationId);
        });
    }

    public void appendAssistantMessage(String agentType, String conversationId, String content) {
        if (content == null || content.isBlank()) return;
        validate(agentType, conversationId);
        String storageAgentType = storageAgentType(agentType);
        String normalized = content.trim();
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            insertMessage(storageAgentType, conversationId, "assistant", normalized, normalized);
            touch(storageAgentType, conversationId);
        });
    }

    public boolean deleteConversation(String agentType, String conversationId) {
        validate(agentType, conversationId);
        String storageAgentType = storageAgentType(agentType);
        return jdbcTemplate.update("""
                DELETE FROM ai_conversations
                WHERE agent_type = ? AND id = ?
                """, storageAgentType, conversationId) > 0;
    }

    public String contextualizeSearchQuery(String question, List<ContextMessage> history) {
        String currentQuestion = question.trim();
        boolean looksLikeFollowUp = currentQuestion.length() <= 30 && (
                currentQuestion.matches(".*(这|那|它|他|她|上述|上面|刚才|前面).*" )
                        || currentQuestion.matches("^(继续|展开|为什么[？?]?|怎么做[？?]?|还有呢[？?]?)$")
        );
        if (!looksLikeFollowUp) return currentQuestion;
        for (int index = history.size() - 1; index >= 0; index--) {
            ContextMessage message = history.get(index);
            if ("user".equals(message.role())) {
                String previous = message.content();
                if (previous.length() > 500) previous = previous.substring(0, 500);
                return "上一轮用户问题：" + previous + "\n当前追问：" + currentQuestion;
            }
        }
        return currentQuestion;
    }

    public String buildPromptWithHistory(String currentPrompt, List<ContextMessage> history) {
        if (history.isEmpty()) return currentPrompt;
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是本会话此前保存的对话，请延续上下文回答。\n")
                .append("<conversation_history>\n");
        for (ContextMessage message : history) {
            prompt.append("<message role=\"").append(message.role()).append("\">\n")
                    .append(message.content())
                    .append("\n</message>\n");
        }
        prompt.append("</conversation_history>\n\n<current_user_message>\n")
                .append(currentPrompt)
                .append("\n</current_user_message>");
        return prompt.toString();
    }

    private List<ConversationMessage> getDisplayMessages(String agentType, String conversationId) {
        return jdbcTemplate.query("""
                SELECT id, role, display_content, created_at
                FROM ai_conversation_messages
                WHERE agent_type = ? AND conversation_id = ?
                ORDER BY id
                """, (resultSet, rowNumber) -> new ConversationMessage(
                resultSet.getLong("id"),
                resultSet.getString("role"),
                resultSet.getString("display_content"),
                resultSet.getTimestamp("created_at").toInstant()
        ), agentType, conversationId);
    }

    private ConversationSummary mapSummary(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ConversationSummary(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getInt("message_count"),
                resultSet.getString("last_message")
        );
    }

    private void insertMessage(
            String agentType,
            String conversationId,
            String role,
            String displayContent,
            String modelContent
    ) {
        jdbcTemplate.update("""
                INSERT INTO ai_conversation_messages
                (agent_type, conversation_id, role, display_content, model_content)
                VALUES (?, ?, ?, ?, ?)
                """, agentType, conversationId, role, displayContent, modelContent);
    }

    private void touch(String agentType, String conversationId) {
        jdbcTemplate.update("""
                UPDATE ai_conversations
                SET updated_at = CURRENT_TIMESTAMP
                WHERE agent_type = ? AND id = ?
                """, agentType, conversationId);
    }

    private String normalize(String content, String fallback) {
        return content == null || content.isBlank() ? fallback : content.trim();
    }

    private String titleFrom(String message) {
        String title = message.replaceAll("\\s+", " ").trim();
        if (title.length() <= TITLE_MAX_LENGTH) return title;
        return title.substring(0, TITLE_MAX_LENGTH) + "…";
    }

    private void validate(String agentType, String conversationId) {
        validateAgentType(agentType);
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("会话 ID 无效");
        }
    }

    private void validateAgentType(String agentType) {
        if (!SUPPORTED_AGENT_TYPES.contains(agentType)) {
            throw new IllegalArgumentException("智能体类型无效");
        }
    }

    private String storageAgentType(String agentType) {
        return publicDemoEnabled ? "public-demo:" + agentType : agentType;
    }

    public record ConversationSummary(
            String id,
            String title,
            Instant createdAt,
            Instant updatedAt,
            int messageCount,
            String lastMessage
    ) {
    }

    public record ConversationMessage(long id, String role, String content, Instant createdAt) {
    }

    public record ContextMessage(String role, String content) {
    }

    public record ConversationDetail(ConversationSummary conversation, List<ConversationMessage> messages) {
    }

    public static class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException(String conversationId) {
            super("找不到会话：" + conversationId);
        }
    }
}

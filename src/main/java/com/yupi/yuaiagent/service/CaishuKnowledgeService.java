package com.yupi.yuaiagent.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 数字菜叔本地 RAG 知识库：增量索引 Markdown、持久化向量并执行语义检索。
 */
@Service
@Slf4j
public class CaishuKnowledgeService {

    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final int TOP_K = 8;
    private static final double MIN_SIMILARITY = 0.45;
    private static final String QUERY_INSTRUCTION =
            "Instruct: Given a user question, retrieve relevant passages from Cai Leilei's WeChat articles that answer the question\nQuery: ";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CaishuArticleParser articleParser;
    private final EmbeddingModel embeddingModel;
    private final Path sourceDirectory;
    private final AtomicBoolean indexing = new AtomicBoolean(false);
    private final AtomicReference<IndexStatus> status = new AtomicReference<>(IndexStatus.idle());
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "caishu-indexer");
        thread.setDaemon(true);
        return thread;
    });

    public CaishuKnowledgeService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            CaishuArticleParser articleParser,
            @Value("${app.caishu.source-directory}") String sourceDirectory,
            @Value("${app.caishu.ollama-base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @Value("${app.caishu.embedding-model:qwen3-embedding:0.6b}") String embeddingModelName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.articleParser = articleParser;
        this.sourceDirectory = Path.of(sourceDirectory).toAbsolutePath().normalize();
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(ollamaBaseUrl).build();
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(embeddingModelName)
                        .keepAlive("30m")
                        .truncate(true)
                        .build())
                .build();
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS caishu_documents (
                    source_path TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT,
                    published_at TEXT,
                    source_url TEXT,
                    category TEXT,
                    content_hash TEXT NOT NULL,
                    chunk_count INTEGER NOT NULL,
                    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS caishu_chunks (
                    id UUID PRIMARY KEY,
                    source_path TEXT NOT NULL REFERENCES caishu_documents(source_path) ON DELETE CASCADE,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    embedding VECTOR(1024) NOT NULL,
                    UNIQUE(source_path, chunk_index)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS caishu_chunks_embedding_hnsw
                ON caishu_chunks USING hnsw (embedding vector_cosine_ops)
                """);
        refreshStoredCounts("idle", "等待检查本地文章");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        startIndexing();
    }

    public IndexStatus startIndexing() {
        if (indexing.compareAndSet(false, true)) {
            indexExecutor.submit(this::synchronizeKnowledgeBase);
        }
        return status.get();
    }

    public IndexStatus getStatus() {
        return status.get();
    }

    public List<SearchResult> search(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (count("caishu_chunks") == 0) {
            throw new IllegalStateException("菜叔文章知识库尚未完成首次索引");
        }

        float[] queryEmbedding = embeddingModel.embed(QUERY_INSTRUCTION + question.trim());
        verifyDimensions(queryEmbedding);
        String vector = toVector(queryEmbedding);
        String sql = """
                SELECT d.title, d.author, d.published_at, d.source_url, d.category, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS similarity
                FROM caishu_chunks c
                JOIN caishu_documents d ON d.source_path = c.source_path
                ORDER BY c.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;
        List<SearchResult> candidates = jdbcTemplate.query(sql, preparedStatement -> {
            preparedStatement.setString(1, vector);
            preparedStatement.setString(2, vector);
            preparedStatement.setInt(3, TOP_K);
        }, (resultSet, rowNumber) -> new SearchResult(
                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("published_at"),
                resultSet.getString("source_url"),
                resultSet.getString("category"),
                resultSet.getString("content"),
                resultSet.getDouble("similarity")
        ));
        return candidates.stream()
                .filter(candidate -> candidate.similarity() >= MIN_SIMILARITY)
                .toList();
    }

    private void synchronizeKnowledgeBase() {
        Instant startedAt = Instant.now();
        try {
            if (!Files.isDirectory(sourceDirectory)) {
                throw new IllegalStateException("菜叔文章目录不存在：" + sourceDirectory);
            }
            List<Path> files;
            try (var paths = Files.walk(sourceDirectory)) {
                files = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                        .sorted()
                        .toList();
            }
            status.set(new IndexStatus("indexing", files.size(), 0, 0, 0,
                    count("caishu_documents"), count("caishu_chunks"), "正在检查文章变化", startedAt, null));

            Map<String, String> storedHashes = new HashMap<>();
            jdbcTemplate.queryForList("SELECT source_path, content_hash FROM caishu_documents")
                    .forEach(row -> storedHashes.put(
                            String.valueOf(row.get("source_path")),
                            String.valueOf(row.get("content_hash"))
                    ));
            Set<String> currentPaths = new HashSet<>();
            int indexed = 0;
            int skipped = 0;
            int processed = 0;

            for (Path file : files) {
                CaishuArticleParser.ParsedArticle article = articleParser.parse(file);
                currentPaths.add(article.sourcePath());
                if (article.contentHash().equals(storedHashes.get(article.sourcePath()))) {
                    skipped++;
                } else {
                    indexArticle(article);
                    indexed++;
                }
                processed++;
                status.set(new IndexStatus("indexing", files.size(), processed, indexed, skipped,
                        count("caishu_documents"), count("caishu_chunks"),
                        "正在处理：" + article.title(), startedAt, null));
            }

            for (String oldPath : storedHashes.keySet()) {
                if (!currentPaths.contains(oldPath)) {
                    jdbcTemplate.update("DELETE FROM caishu_documents WHERE source_path = ?", oldPath);
                }
            }
            String message = indexed == 0 ? "知识库已是最新" : "新增或更新了 " + indexed + " 篇文章";
            status.set(new IndexStatus("ready", files.size(), files.size(), indexed, skipped,
                    count("caishu_documents"), count("caishu_chunks"), message, startedAt, Instant.now()));
            log.info("Caishu knowledge base ready, articles: {}, chunks: {}, updated: {}",
                    status.get().articleCount(), status.get().chunkCount(), indexed);
        } catch (Exception e) {
            log.error("Failed to index Caishu knowledge base", e);
            IndexStatus previous = status.get();
            status.set(new IndexStatus("error", previous.totalFiles(), previous.processedFiles(),
                    previous.indexedFiles(), previous.skippedFiles(), countSafely("caishu_documents"),
                    countSafely("caishu_chunks"), readableError(e), startedAt, Instant.now()));
        } finally {
            indexing.set(false);
        }
    }

    private void indexArticle(CaishuArticleParser.ParsedArticle article) {
        if (article.chunks().isEmpty()) {
            throw new IllegalArgumentException("文章没有可索引正文：" + article.title());
        }
        List<String> embeddingInputs = article.chunks().stream()
                .map(chunk -> article.title() + "\n\n" + chunk)
                .toList();
        List<float[]> embeddings = new ArrayList<>();
        for (int start = 0; start < embeddingInputs.size(); start += 24) {
            int end = Math.min(start + 24, embeddingInputs.size());
            embeddings.addAll(embeddingModel.embed(embeddingInputs.subList(start, end)));
        }
        if (embeddings.size() != article.chunks().size()) {
            throw new IllegalStateException("Embedding 返回数量与文章分块数量不一致");
        }
        embeddings.forEach(this::verifyDimensions);

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            jdbcTemplate.update("DELETE FROM caishu_documents WHERE source_path = ?", article.sourcePath());
            jdbcTemplate.update("""
                            INSERT INTO caishu_documents
                            (source_path, title, author, published_at, source_url, category, content_hash, chunk_count)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    article.sourcePath(), article.title(), article.author(), article.publishedAt(),
                    article.sourceUrl(), article.category(), article.contentHash(), article.chunks().size());
            jdbcTemplate.batchUpdate("""
                    INSERT INTO caishu_chunks (id, source_path, chunk_index, content, embedding)
                    VALUES (?, ?, ?, ?, CAST(? AS vector))
                    """, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement preparedStatement, int index) throws java.sql.SQLException {
                    preparedStatement.setObject(1, UUID.randomUUID());
                    preparedStatement.setString(2, article.sourcePath());
                    preparedStatement.setInt(3, index);
                    preparedStatement.setString(4, article.chunks().get(index));
                    preparedStatement.setString(5, toVector(embeddings.get(index)));
                }

                @Override
                public int getBatchSize() {
                    return article.chunks().size();
                }
            });
        });
    }

    private void refreshStoredCounts(String state, String message) {
        status.set(new IndexStatus(state, 0, 0, 0, 0,
                count("caishu_documents"), count("caishu_chunks"), message, null, null));
    }

    private int count(String table) {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    private int countSafely(String table) {
        try {
            return count(table);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void verifyDimensions(float[] embedding) {
        if (embedding.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("Embedding 向量维度应为 " + EMBEDDING_DIMENSIONS + "，实际为 " + embedding.length);
        }
    }

    private String toVector(float[] embedding) {
        StringBuilder vector = new StringBuilder(embedding.length * 10).append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) vector.append(',');
            vector.append(embedding[index]);
        }
        return vector.append(']').toString();
    }

    private String readableError(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @PreDestroy
    void shutdown() {
        indexExecutor.shutdownNow();
    }

    public record SearchResult(
            String title,
            String author,
            String publishedAt,
            String sourceUrl,
            String category,
            String content,
            double similarity
    ) {
    }

    public record IndexStatus(
            String state,
            int totalFiles,
            int processedFiles,
            int indexedFiles,
            int skippedFiles,
            int articleCount,
            int chunkCount,
            String message,
            Instant startedAt,
            Instant completedAt
    ) {
        static IndexStatus idle() {
            return new IndexStatus("idle", 0, 0, 0, 0, 0, 0, "等待初始化", null, null);
        }
    }
}

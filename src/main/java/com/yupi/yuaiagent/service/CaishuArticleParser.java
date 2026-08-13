package com.yupi.yuaiagent.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析菜叔微信公众号 Markdown 存档，并按自然段生成适合向量检索的文本块。
 */
@Component
public class CaishuArticleParser {

    private static final int TARGET_CHUNK_SIZE = 850;
    private static final int MAX_CHUNK_SIZE = 1100;
    private static final int OVERLAP_SIZE = 100;
    private static final Pattern FRONT_MATTER = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n");

    public ParsedArticle parse(Path path) throws IOException {
        String markdown = Files.readString(path, StandardCharsets.UTF_8);
        Matcher frontMatterMatcher = FRONT_MATTER.matcher(markdown);
        String frontMatter = frontMatterMatcher.find() ? frontMatterMatcher.group(1) : "";
        String body = frontMatterMatcher.find(0) ? markdown.substring(frontMatterMatcher.end()) : markdown;

        String title = metadata(frontMatter, "title");
        if (title.isBlank()) {
            Matcher heading = Pattern.compile("(?m)^#\\s+(.+)$").matcher(body);
            title = heading.find() ? heading.group(1).trim() : path.getFileName().toString().replaceFirst("\\.md$", "");
        }

        String content = extractOriginalContent(body);
        String contentHash = metadata(frontMatter, "content_hash");
        if (contentHash.isBlank()) {
            contentHash = sha256(content);
        }

        return new ParsedArticle(
                path.toAbsolutePath().normalize().toString(),
                title,
                metadata(frontMatter, "author"),
                metadata(frontMatter, "published_at"),
                metadata(frontMatter, "source_url"),
                metadata(frontMatter, "category"),
                contentHash,
                content,
                split(content)
        );
    }

    List<String> split(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("(?m)^#{1,6}\\s*", "")
                    .replaceAll("(?m)^>\\s?", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (normalized.isBlank()) {
                continue;
            }
            for (int start = 0; start < normalized.length(); start += MAX_CHUNK_SIZE) {
                paragraphs.add(normalized.substring(start, Math.min(start + MAX_CHUNK_SIZE, normalized.length())));
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (!current.isEmpty() && current.length() + paragraph.length() + 2 > TARGET_CHUNK_SIZE) {
                String completed = current.toString().trim();
                chunks.add(completed);
                current = new StringBuilder(tail(completed, OVERLAP_SIZE));
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private String extractOriginalContent(String body) {
        int contentStart = body.indexOf("## 原始内容");
        String content = contentStart >= 0 ? body.substring(contentStart + "## 原始内容".length()) : body;
        int attachmentStart = content.indexOf("\n## 附件与媒体");
        int recordStart = content.indexOf("\n## 记录信息");
        int end = content.length();
        if (attachmentStart >= 0) end = Math.min(end, attachmentStart);
        if (recordStart >= 0) end = Math.min(end, recordStart);
        content = content.substring(0, end);

        Matcher finished = Pattern.compile("(?m)^\\s*[（(]完[）)]\\s*$").matcher(content);
        if (finished.find()) {
            content = content.substring(0, finished.start());
        }
        return content.replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String metadata(String frontMatter, String key) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s*(.*)$").matcher(frontMatter);
        if (!matcher.find()) return "";
        String value = matcher.group(1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\\"", "\"");
    }

    private String tail(String value, int size) {
        return value.substring(Math.max(0, value.length() - size));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        }
    }

    public record ParsedArticle(
            String sourcePath,
            String title,
            String author,
            String publishedAt,
            String sourceUrl,
            String category,
            String contentHash,
            String content,
            List<String> chunks
    ) {
    }
}

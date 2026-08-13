package com.yupi.yuaiagent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CaishuArticleParserTest {

    @TempDir
    Path tempDirectory;

    private final CaishuArticleParser parser = new CaishuArticleParser();

    @Test
    void parsesMetadataAndOnlyIndexesOriginalContent() throws Exception {
        Path article = tempDirectory.resolve("article.md");
        Files.writeString(article, """
                ---
                title: "测试文章"
                author: "蔡垒磊（公众号：请辩）"
                source_url: "https://example.com/article"
                published_at: "2026-01-01 12:00:00"
                category: "思维与认知"
                content_hash: "fixed-hash"
                ---

                # 测试文章

                > [!info] 来源
                > 这里不是正文

                ## 原始内容

                第一段是需要建立向量的正文。

                第二段也是正文。

                （完）

                这里是推广信息，不应进入知识库。

                ## 附件与媒体

                - https://example.com/image.png
                """);

        CaishuArticleParser.ParsedArticle parsed = parser.parse(article);

        assertThat(parsed.title()).isEqualTo("测试文章");
        assertThat(parsed.author()).contains("蔡垒磊");
        assertThat(parsed.sourceUrl()).isEqualTo("https://example.com/article");
        assertThat(parsed.contentHash()).isEqualTo("fixed-hash");
        assertThat(parsed.content()).contains("第一段", "第二段")
                .doesNotContain("推广信息", "附件与媒体", "这里不是正文");
        assertThat(parsed.chunks()).isNotEmpty();
    }

    @Test
    void splitsLongTextWithBoundedChunks() {
        String content = ("这是一个用于测试自然段分块的句子。".repeat(40) + "\n\n").repeat(8);

        assertThat(parser.split(content))
                .hasSizeGreaterThan(2)
                .allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(1250));
    }
}

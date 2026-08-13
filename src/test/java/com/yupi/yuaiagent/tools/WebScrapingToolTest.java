package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    @Test
    void scrapeWebPage() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String url = "https://example.com/";
        String result = webScrapingTool.scrapeWebPage(url);
        assertFalse(result.startsWith("Error scraping web page"), result);
        assertTrue(result.contains("Example Domain"), result);
    }
}

package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WebSearchToolTest {

    @Test
    void searchWeb() {
        String searchApiKey = System.getenv("SEARCH_API_KEY");
        assumeTrue(searchApiKey != null && !searchApiKey.isBlank());
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        String query = "上海天气";
        String result = webSearchTool.searchWeb(query);
        assertFalse(result.startsWith("Error searching Baidu"), result);
        assertFalse(result.startsWith("No Baidu search results"), result);
        assertTrue(result.length() > 20, result);
    }
}

package com.yupi.yuaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        if (StrUtil.isBlank(apiKey)) {
            return "Error searching Baidu: SEARCH_API_KEY is not configured";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            try (HttpResponse response = HttpRequest.get(SEARCH_API_URL)
                    .form(paramMap)
                    .execute()) {
                if (!response.isOk()) {
                    return "Error searching Baidu: HTTP " + response.getStatus();
                }
                JSONObject jsonObject = JSONUtil.parseObj(response.body());
            // 提取 organic_results 部分
                JSONArray organicResults = jsonObject.getJSONArray("organic_results");
                if (organicResults == null || organicResults.isEmpty()) {
                    return "No Baidu search results found for: " + query;
                }
            // 拼接搜索结果为字符串
                return organicResults.stream()
                        .limit(5)
                        .map(obj -> ((JSONObject) obj).toString())
                        .collect(Collectors.joining(","));
            }
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}

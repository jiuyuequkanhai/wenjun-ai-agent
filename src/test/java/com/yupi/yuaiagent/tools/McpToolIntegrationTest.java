package com.yupi.yuaiagent.tools;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class McpToolIntegrationTest {

    @Autowired
    private ToolCallback[] allTools;

    @Test
    void listMcpTools() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_MCP_INTEGRATION_TESTS")));
        Arrays.stream(allTools)
                .map(ToolCallback::getToolDefinition)
                .filter(definition -> definition.name().contains("maps")
                        || definition.name().contains("image"))
                .forEach(definition -> System.out.printf(
                        "MCP_TOOL name=%s schema=%s%n",
                        definition.name(),
                        definition.inputSchema()));
    }

    @Test
    void callImageAndAmapTools() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_MCP_INTEGRATION_TESTS")));

        call("spring_ai_mcp_client_yu_image_search_mcp_server_searchImage",
                Map.of("query", "office workspace"));

        call("spring_ai_mcp_client_amap_maps_maps_geo",
                Map.of("address", "上海市黄浦区人民大道200号", "city", "上海"));
        call("spring_ai_mcp_client_amap_maps_maps_regeocode",
                Map.of("location", "121.4737,31.2304"));
        call("spring_ai_mcp_client_amap_maps_maps_ip_location",
                Map.of("ip", "114.114.114.114"));
        call("spring_ai_mcp_client_amap_maps_maps_weather",
                Map.of("city", "上海"));

        String textSearchResult = call("spring_ai_mcp_client_amap_maps_maps_text_search",
                Map.of("keywords", "外滩", "city", "上海"));
        call("spring_ai_mcp_client_amap_maps_maps_around_search",
                Map.of("keywords", "咖啡", "location", "121.4737,31.2304", "radius", "1000"));

        String textSearchPayload = JSONUtil.parseArray(textSearchResult)
                .getJSONObject(0)
                .getStr("text");
        String poiId = JSONUtil.parseObj(textSearchPayload)
                .getJSONArray("pois")
                .getJSONObject(0)
                .getStr("id");
        assertFalse(poiId.isBlank(), textSearchResult);
        call("spring_ai_mcp_client_amap_maps_maps_search_detail",
                Map.of("id", poiId));

        Map<String, Object> route = Map.of(
                "origin", "121.4737,31.2304",
                "destination", "121.4903,31.2411");
        call("spring_ai_mcp_client_amap_maps_maps_bicycling", route);
        call("spring_ai_mcp_client_amap_maps_maps_direction_walking", route);
        call("spring_ai_mcp_client_amap_maps_maps_direction_driving", route);
        call("spring_ai_mcp_client_amap_maps_maps_direction_transit_integrated",
                Map.of(
                        "origin", "121.4737,31.2304",
                        "destination", "121.4903,31.2411",
                        "city", "上海",
                        "cityd", "上海"));
        call("spring_ai_mcp_client_amap_maps_maps_distance",
                Map.of(
                        "origins", "121.4737,31.2304",
                        "destination", "121.4903,31.2411",
                        "type", "1"));
    }

    private String call(String toolName, Map<String, ?> input) {
        ToolCallback tool = Arrays.stream(allTools)
                .filter(callback -> callback.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not registered: " + toolName));
        String result = tool.call(JSONUtil.toJsonStr(input));
        assertNotNull(result);
        assertFalse(result.isBlank(), toolName);
        assertFalse(result.toLowerCase().contains("error"), result);
        assertFalse(result.contains("\\\"status\\\":\\\"0\\\""), result);
        System.out.println("MCP_OK " + toolName);
        return result;
    }
}

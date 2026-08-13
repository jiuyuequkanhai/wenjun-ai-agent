package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.public-demo.enabled=true")
class PublicDemoToolRegistrationTest {

    @Autowired
    private ToolCallback[] allTools;

    @Test
    void dangerousLocalToolsAreNotRegistered() {
        Set<String> names = Arrays.stream(allTools)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertFalse(names.contains("executeTerminalCommand"));
        assertFalse(names.contains("readFile"));
        assertFalse(names.contains("writeFile"));
        assertFalse(names.contains("downloadResource"));
        assertTrue(names.contains("generatePDF"));
        assertTrue(names.contains("searchWeb"));
        assertTrue(names.contains("scrapeWebPage"));
        assertTrue(names.stream().anyMatch(name -> name.endsWith("maps_weather")));
        assertTrue(names.stream().anyMatch(name -> name.endsWith("searchImage")));
    }
}

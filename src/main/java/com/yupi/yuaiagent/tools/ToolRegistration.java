package com.yupi.yuaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistration.class);

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${app.public-demo.enabled:false}")
    private boolean publicDemoEnabled;

    @Bean
    public ToolCallback[] allTools(ObjectProvider<ToolCallbackProvider> mcpToolCallbackProvider) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        ToolCallback[] localTools;
        if (publicDemoEnabled) {
            log.warn("Public demo mode enabled: terminal, generic file operations and resource download are disabled");
            localTools = ToolCallbacks.from(
                    webSearchTool,
                    webScrapingTool,
                    pdfGenerationTool,
                    terminateTool
            );
        } else {
            localTools = ToolCallbacks.from(
                    fileOperationTool,
                    webSearchTool,
                    webScrapingTool,
                    resourceDownloadTool,
                    terminalOperationTool,
                    pdfGenerationTool,
                    terminateTool
            );
        }
        ToolCallback[] mcpTools = mcpToolCallbackProvider.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);
        return Stream.concat(Arrays.stream(localTools), Arrays.stream(mcpTools))
                .toArray(ToolCallback[]::new);
    }
}

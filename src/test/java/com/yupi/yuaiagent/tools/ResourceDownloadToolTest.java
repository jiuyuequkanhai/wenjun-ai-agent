package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.constant.FileConstant;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceDownloadToolTest {

    @Test
    public void testDownloadResource() throws Exception {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "https://example.com/";
        String fileName = "resource-download-test.html";
        Path outputPath = Path.of(FileConstant.FILE_SAVE_DIR, "download", fileName);

        try {
            String result = tool.downloadResource(url, fileName);
            assertTrue(result.startsWith("Resource downloaded successfully"), result);
            assertTrue(Files.size(outputPath) > 0);
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }
}

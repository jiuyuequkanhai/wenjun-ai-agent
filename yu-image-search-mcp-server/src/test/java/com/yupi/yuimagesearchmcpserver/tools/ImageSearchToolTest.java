package com.yupi.yuimagesearchmcpserver.tools;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ImageSearchToolTest {

    @Test
    void searchMediumImages() {
        String apiKey = System.getenv("PEXELS_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank());

        List<String> images = new ImageSearchTool(apiKey).searchMediumImages("office workspace");

        assertFalse(images.isEmpty());
        assertTrue(images.stream().allMatch(url -> {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        }));
    }
}

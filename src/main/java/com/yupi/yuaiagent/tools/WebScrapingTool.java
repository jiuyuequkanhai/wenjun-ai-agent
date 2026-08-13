package com.yupi.yuaiagent.tools;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.InetAddress;
import java.net.URI;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            String currentUrl = url;
            for (int redirectCount = 0; redirectCount <= 5; redirectCount++) {
                validatePublicHttpUrl(currentUrl);
                Connection.Response response = Jsoup.connect(currentUrl)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalArgumentException("Redirect response has no target URL");
                    }
                    currentUrl = URI.create(currentUrl).resolve(location).toString();
                    continue;
                }
                if (status >= 400) {
                    throw new IllegalArgumentException("HTTP " + status);
                }
                Document document = response.parse();
                return document.html();
            }
            throw new IllegalArgumentException("Too many redirects");
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }

    private void validatePublicHttpUrl(String url) throws Exception {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL host is missing");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            byte[] bytes = address.getAddress();
            boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || uniqueLocalIpv6) {
                throw new IllegalArgumentException("Private or local network addresses are not allowed");
            }
        }
    }
}

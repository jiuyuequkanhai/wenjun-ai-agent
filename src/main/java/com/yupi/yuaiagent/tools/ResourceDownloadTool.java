package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

/**
 * 资源下载工具
 */
public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url, @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("Invalid download file name");
            }
            Path baseDir = Path.of(fileDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileName).normalize();
            if (!filePath.startsWith(baseDir) || filePath.equals(baseDir)) {
                throw new IllegalArgumentException("Invalid download file name");
            }
            // 创建目录
            FileUtil.mkdir(baseDir.toFile());
            // 使用 Hutool 的 downloadFile 方法下载资源
            HttpUtil.downloadFile(url, filePath.toFile());
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}

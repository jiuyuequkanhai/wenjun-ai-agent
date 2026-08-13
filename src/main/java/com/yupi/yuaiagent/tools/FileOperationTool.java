package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

/**
 * 文件操作工具类（提供文件读写功能）
 */
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        try {
            return FileUtil.readUtf8String(resolveFile(fileName).toFile());
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        try {
            Path filePath = resolveFile(fileName);
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath.toFile());
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    private Path resolveFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }
        Path baseDir = Path.of(FILE_DIR).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(fileName).normalize();
        if (!filePath.startsWith(baseDir) || filePath.equals(baseDir)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return filePath;
    }
}

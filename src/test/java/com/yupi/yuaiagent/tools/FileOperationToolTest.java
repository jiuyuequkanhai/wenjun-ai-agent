package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.constant.FileConstant;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationToolTest {

    @Test
    void writeAndReadFile() throws Exception {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "中文文件读写测试.txt";
        String content = "文俊的超级助手文件读写测试";
        Path outputPath = Path.of(FileConstant.FILE_SAVE_DIR, "file", fileName);

        try {
            String result = fileOperationTool.writeFile(fileName, content);
            assertTrue(result.startsWith("File written successfully"), result);
            assertEquals(content, fileOperationTool.readFile(fileName));
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }
}

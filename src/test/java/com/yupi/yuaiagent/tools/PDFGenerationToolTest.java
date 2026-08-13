package com.yupi.yuaiagent.tools;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.yupi.yuaiagent.constant.FileConstant;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() throws Exception {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "中文字体生成测试.pdf";
        String content = "文俊的超级助手 PDF 中文生成测试";
        Path outputPath = Path.of(FileConstant.FILE_SAVE_DIR, "pdf", fileName);

        try {
            String result = tool.generatePDF(fileName, content);

            assertTrue(result.startsWith("PDF generated successfully"), result);
            assertTrue(Files.exists(outputPath));
            assertTrue(Files.size(outputPath) > 0);
            try (PdfDocument document = new PdfDocument(new PdfReader(outputPath.toString()))) {
                assertTrue(PdfTextExtractor.getTextFromPage(document.getPage(1)).contains(content));
            }
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }
}

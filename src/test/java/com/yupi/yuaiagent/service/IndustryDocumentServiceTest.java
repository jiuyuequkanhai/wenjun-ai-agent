package com.yupi.yuaiagent.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryDocumentServiceTest {

    private final IndustryDocumentService documentService = new IndustryDocumentService();

    @Test
    void shouldExtractPdfAndDocxAndBuildPrompt() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "retail-research.pdf", "application/pdf", createPdf("PDF_UNIQUE_RETAIL_CONTENT")
        );
        MockMultipartFile docx = new MockMultipartFile(
                "files", "saas-research.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx("DOCX_UNIQUE_SAAS_CONTENT")
        );

        List<IndustryDocumentService.UploadedDocument> uploaded =
                documentService.uploadDocuments(List.of(pdf, docx), "research_test_01");

        assertEquals(2, uploaded.size());
        assertEquals(1, uploaded.getFirst().pageCount());
        assertTrue(uploaded.stream().allMatch(document -> document.characterCount() > 0));

        String prompt = documentService.buildPromptWithDocuments(
                "请分析资料", "research_test_01", uploaded.stream().map(IndustryDocumentService.UploadedDocument::id).toList()
        );
        assertTrue(prompt.contains("PDF_UNIQUE_RETAIL_CONTENT"));
        assertTrue(prompt.contains("DOCX_UNIQUE_SAAS_CONTENT"));
        assertTrue(prompt.contains("<uploaded_documents>"));
    }

    @Test
    void shouldRejectUnsupportedFilesAndCrossSessionAccess() throws Exception {
        MockMultipartFile text = new MockMultipartFile(
                "files", "notes.txt", "text/plain", "not allowed".getBytes()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> documentService.uploadDocuments(List.of(text), "research_test_02")
        );

        MockMultipartFile docx = new MockMultipartFile(
                "files", "private.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx("PRIVATE_CONTENT")
        );
        IndustryDocumentService.UploadedDocument uploaded =
                documentService.uploadDocuments(List.of(docx), "research_owner").getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () -> documentService.buildPromptWithDocuments(
                        "读取", "research_other", List.of(uploaded.id())
                )
        );
    }

    private byte[] createPdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(output);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {
            document.add(new Paragraph(text));
        }
        return output.toByteArray();
    }

    private byte[] createDocx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return output.toByteArray();
    }
}

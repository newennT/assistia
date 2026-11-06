package com.assistia.service;

import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeDocumentCategory;
import com.assistia.service.ingestion.ApproximateTokenEstimator;
import com.assistia.service.ingestion.DocumentChunkingService;
import com.assistia.service.ingestion.DocumentTextExtractor;
import com.assistia.service.ingestion.ExtractedPage;
import com.assistia.service.ingestion.KnowledgeDocumentLifecycleService;
import com.assistia.service.ingestion.KnowledgeDocumentStorage;
import com.assistia.service.ingestion.MarkdownTextExtractor;
import com.assistia.service.ingestion.PdfTextExtractor;
import com.assistia.service.ingestion.StoredDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentIngestionServiceTest {
    private final KnowledgeDocumentLifecycleService lifecycle = mock(KnowledgeDocumentLifecycleService.class);
    private final KnowledgeDocumentStorage storage = mock(KnowledgeDocumentStorage.class);
    private final DocumentChunkingService chunking =
            new DocumentChunkingService(new ApproximateTokenEstimator(), 700, 100);
    private final List<DocumentTextExtractor> extractors =
            List.of(new MarkdownTextExtractor(), new PdfTextExtractor());
    private final KnowledgeDocumentIngestionService service =
            new KnowledgeDocumentIngestionService(lifecycle, storage, chunking, extractors, 1024);

    @Test
    void importsMarkdownWithSha256AndNullEmbeddingsRemainUnwritten() throws Exception {
        byte[] content = "# Guide\n\nLe service est disponible.".getBytes(StandardCharsets.UTF_8);
        StoredDocument candidate = new StoredDocument(7L, 1, "knowledge/7/1.md");
        when(lifecycle.begin(eq(null), eq(2L), eq("Guide"), eq(KnowledgeDocumentCategory.GENERAL),
                eq("Guide.md"), eq("text/markdown"), eq((long) content.length), any(), eq(".md")))
                .thenReturn(candidate);
        KnowledgeDocument persisted = mock(KnowledgeDocument.class);
        when(persisted.getId()).thenReturn(7L);
        when(persisted.getTitle()).thenReturn("Guide");
        when(persisted.getCategory()).thenReturn(KnowledgeDocumentCategory.GENERAL);
        when(persisted.getCurrentVersion()).thenReturn(0);
        when(persisted.getIndexingVersion()).thenReturn(1);
        when(lifecycle.findById(7L)).thenReturn(persisted);

        service.importDocument(new MockMultipartFile("file", "Guide.md", "application/octet-stream", content),
                null, null, null, 2L);

        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        verify(lifecycle).begin(null, 2L, "Guide", KnowledgeDocumentCategory.GENERAL, "Guide.md",
                "text/markdown", (long) content.length, expectedHash, ".md");
        verify(storage).store(candidate.storageKey(), content);
        verify(lifecycle).complete(eq(candidate), any(), any());
    }

    @Test
    void rejectsUnsupportedFilesAndOverLimitUploadsBeforeCreatingRecords() {
        assertThatThrownBy(() -> service.importDocument(
                new MockMultipartFile("file", "note.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)),
                null, null, null, 2L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.importDocument(
                new MockMultipartFile("file", "large.md", "text/markdown", new byte[1025]),
                null, null, null, 2L))
                .isInstanceOf(ResponseStatusException.class);
        verify(lifecycle, never()).begin(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void marksInvalidPdfAsFailedWithoutCompletingAnyChunks() {
        byte[] invalidPdf = "%PDF-not valid".getBytes(StandardCharsets.UTF_8);
        StoredDocument candidate = new StoredDocument(9L, 1, "knowledge/9/1.pdf");
        when(lifecycle.begin(eq(null), eq(2L), eq("bad"), eq(KnowledgeDocumentCategory.GENERAL),
                eq("bad.pdf"), eq("application/pdf"), eq((long) invalidPdf.length), any(), eq(".pdf")))
                .thenReturn(candidate);

        assertThatThrownBy(() -> service.importDocument(
                new MockMultipartFile("file", "bad.pdf", "application/pdf", invalidPdf),
                null, null, null, 2L))
                .isInstanceOf(ResponseStatusException.class);

        verify(lifecycle).fail(candidate);
        verify(lifecycle, never()).complete(any(), any(), any());
    }

    @Test
    void importsTextPdfAndPassesSourcePagesIntoChunkCreation() throws Exception {
        byte[] pdf = createPdf();
        StoredDocument candidate = new StoredDocument(11L, 3, "knowledge/11/3.pdf");
        when(lifecycle.begin(eq(4L), eq(2L), eq(null), eq(null),
                eq("manual.pdf"), eq("application/pdf"), eq((long) pdf.length), any(), eq(".pdf")))
                .thenReturn(candidate);
        KnowledgeDocument persisted = mock(KnowledgeDocument.class);
        when(persisted.getId()).thenReturn(11L);
        when(persisted.getTitle()).thenReturn("manual");
        when(persisted.getCategory()).thenReturn(KnowledgeDocumentCategory.GENERAL);
        when(persisted.getCurrentVersion()).thenReturn(2);
        when(persisted.getIndexingVersion()).thenReturn(3);
        when(lifecycle.findById(11L)).thenReturn(persisted);

        service.importDocument(new MockMultipartFile("file", "manual.pdf", "application/pdf", pdf),
                4L, null, null, 2L);

        org.mockito.ArgumentCaptor<List<ExtractedPage>> pages = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<com.assistia.service.ingestion.ChunkDraft>> drafts =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(lifecycle).complete(eq(candidate), pages.capture(), drafts.capture());
        assertThat(pages.getValue()).extracting(ExtractedPage::pageNumber).containsExactly(1);
        assertThat(drafts.getValue()).extracting(com.assistia.service.ingestion.ChunkDraft::sourcePage)
                .containsOnly(1);
    }

    @Test
    void rejectsBlankMarkdownAndMarksTheCandidateFailed() {
        byte[] emptyText = " \n\t".getBytes(StandardCharsets.UTF_8);
        StoredDocument candidate = new StoredDocument(12L, 1, "knowledge/12/1.md");
        when(lifecycle.begin(eq(null), eq(2L), eq("empty"), eq(KnowledgeDocumentCategory.GENERAL),
                eq("empty.md"), eq("text/markdown"), eq((long) emptyText.length), any(), eq(".md")))
                .thenReturn(candidate);

        assertThatThrownBy(() -> service.importDocument(
                new MockMultipartFile("file", "empty.md", "text/markdown", emptyText),
                null, null, null, 2L))
                .isInstanceOf(ResponseStatusException.class);
        verify(lifecycle).fail(candidate);
        verify(lifecycle, never()).complete(any(), any(), any());
    }

    @Test
    void rejectsPdfWithoutSignatureBeforeCreatingDocument() {
        assertThatThrownBy(() -> service.importDocument(
                new MockMultipartFile("file", "bad.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)),
                null, null, null, 2L))
                .isInstanceOf(ResponseStatusException.class);
        verify(lifecycle, never()).begin(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    private static byte[] createPdf() throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument();
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Text for ingestion");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}

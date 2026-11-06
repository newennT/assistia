package com.assistia.service.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {
    private final MarkdownTextExtractor markdown = new MarkdownTextExtractor();
    private final PdfTextExtractor pdf = new PdfTextExtractor();

    @Test
    void readsUtf8MarkdownAndNormalizesLineEndingsWithoutRemovingStructure() throws Exception {
        var page = markdown.extract("# Réseau\r\n\r\n- Étape 1\r\n".getBytes(StandardCharsets.UTF_8)).getFirst();

        assertThat(page.pageNumber()).isNull();
        assertThat(page.content()).isEqualTo("# Réseau\n\n- Étape 1\n");
    }

    @Test
    void rejectsMalformedUtf8AndBinaryMarkdown() {
        assertThatThrownBy(() -> markdown.extract(new byte[] {(byte) 0xC3, 0x28}))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> markdown.extract("texte\u0001".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void extractsPdfTextPageByPage() throws Exception {
        var pages = pdf.extract(createPdf());

        assertThat(pages).extracting(ExtractedPage::pageNumber).containsExactly(1, 2);
        assertThat(pages.getFirst().content()).contains("Première page");
        assertThat(pages.get(1).content()).contains("Deuxième page");
    }

    @Test
    void rejectsInvalidPdf() {
        assertThatThrownBy(() -> pdf.extract("%PDF-invalid".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }

    private static byte[] createPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTextPage(document, "Première page");
            addTextPage(document, "Deuxième page");
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void addTextPage(PDDocument document, String text) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(50, 700);
            stream.showText(text);
            stream.endText();
        }
    }
}

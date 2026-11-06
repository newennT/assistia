package com.assistia.service.ingestion;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MarkdownTextExtractor implements DocumentTextExtractor {
    @Override
    public boolean supports(String extension) {
        return extension.equals("md") || extension.equals("markdown");
    }

    @Override
    public List<ExtractedPage> extract(byte[] bytes) throws IOException {
        try {
            String markdown = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');
            if (markdown.indexOf('\0') >= 0) {
                throw new IOException("Le document Markdown contient des données binaires.");
            }
            if (markdown.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                    && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')) {
                throw new IOException("Le document Markdown contient des caractères de contrôle.");
            }
            return List.of(new ExtractedPage(null, markdown));
        } catch (CharacterCodingException exception) {
            throw new IOException("Le document Markdown n'est pas encodé en UTF-8.", exception);
        }
    }
}

package com.assistia.service.ingestion;

import java.io.IOException;
import java.util.List;

public interface DocumentTextExtractor {
    boolean supports(String extension);

    List<ExtractedPage> extract(byte[] bytes) throws IOException;
}

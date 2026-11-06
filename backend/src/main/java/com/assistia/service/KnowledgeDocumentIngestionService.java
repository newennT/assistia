package com.assistia.service;

import com.assistia.dto.knowledge.KnowledgeDocumentResponse;
import com.assistia.entity.KnowledgeDocumentCategory;
import com.assistia.service.ingestion.DocumentChunkingService;
import com.assistia.service.ingestion.DocumentTextExtractor;
import com.assistia.service.ingestion.ExtractedPage;
import com.assistia.service.ingestion.KnowledgeDocumentLifecycleService;
import com.assistia.service.ingestion.KnowledgeDocumentStorage;
import com.assistia.service.ingestion.ChunkDraft;
import com.assistia.service.ingestion.StoredDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeDocumentIngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentIngestionService.class);
    private final KnowledgeDocumentLifecycleService lifecycle;
    private final KnowledgeDocumentStorage storage;
    private final DocumentChunkingService chunking;
    private final List<DocumentTextExtractor> extractors;
    private final long maxFileSize;

    public KnowledgeDocumentIngestionService(
            KnowledgeDocumentLifecycleService lifecycle,
            KnowledgeDocumentStorage storage,
            DocumentChunkingService chunking,
            List<DocumentTextExtractor> extractors,
            @Value("${assistia.knowledge.max-file-size-bytes:20971520}") long maxFileSize) {
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.chunking = chunking;
        this.extractors = extractors;
        this.maxFileSize = maxFileSize;
    }

    public KnowledgeDocumentResponse importDocument(
            MultipartFile upload,
            Long documentId,
            String title,
            KnowledgeDocumentCategory category,
            Long userId) {
        if (upload == null || upload.isEmpty() || upload.getSize() > maxFileSize) {
            throw new ResponseStatusException(upload != null && upload.getSize() > maxFileSize
                    ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST,
                    upload != null && upload.getSize() > maxFileSize
                            ? "Le fichier dépasse la taille maximale autorisée."
                            : "Le fichier est vide.");
        }
        String filename = sanitizeFilename(upload.getOriginalFilename());
        String extension = extensionOf(filename);
        DocumentTextExtractor extractor = extractors.stream()
                .filter(candidate -> candidate.supports(extension))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Seuls les fichiers Markdown et PDF sont acceptés."));
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier importé est illisible.");
        }
        if (bytes.length == 0 || bytes.length > maxFileSize) {
            throw new ResponseStatusException(bytes.length > maxFileSize
                    ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST,
                    "La taille du fichier importé est invalide.");
        }
        if (extension.equals("pdf") && !hasPdfSignature(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier PDF est invalide.");
        }
        String digest = sha256(bytes);
        String safeTitle = title == null || title.isBlank()
                ? filename.substring(0, filename.lastIndexOf('.'))
                : title.strip();
        if (safeTitle.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre est trop long.");
        }
        KnowledgeDocumentCategory safeCategory = category == null ? KnowledgeDocumentCategory.GENERAL : category;
        String mimeType = extension.equals("pdf") ? "application/pdf" : "text/markdown";
        StoredDocument candidate;
        try {
            candidate = lifecycle.begin(documentId, userId,
                    documentId == null || title != null && !title.isBlank() ? safeTitle : null,
                    documentId == null ? safeCategory : category,
                    filename, mimeType, bytes.length, digest, "." + extension);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un document de contenu identique existe déjà.");
        }

        try {
            storage.store(candidate.storageKey(), bytes);
        } catch (IOException exception) {
            fail(candidate, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le fichier n'a pas pu être stocké.");
        }

        List<ExtractedPage> pages;
        try {
            pages = extractor.extract(bytes);
        } catch (IOException exception) {
            fail(candidate, exception);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le document ne contient pas de texte exploitable ou son format est invalide.");
        }
        if (pages.stream().noneMatch(page -> hasUsefulText(page.content()))) {
            IllegalArgumentException failure = new IllegalArgumentException("Aucun texte exploitable.");
            fail(candidate, failure);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le document ne contient pas de texte exploitable.");
        }

        try {
            List<ChunkDraft> drafts = chunking.chunk(pages);
            lifecycle.complete(candidate, pages, drafts);
            return KnowledgeDocumentResponse.from(lifecycle.findById(candidate.documentId()));
        } catch (RuntimeException exception) {
            fail(candidate, exception);
            throw exception;
        }
    }

    private void fail(StoredDocument candidate, Throwable cause) {
        try {
            lifecycle.fail(candidate);
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
            LOGGER.error("Échec de mise à jour de l'état d'import du document {}.", candidate.documentId(), failure);
        }
    }

    private static boolean hasUsefulText(String text) {
        return text != null && text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String sanitizeFilename(String suppliedName) {
        if (suppliedName == null || suppliedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du fichier est obligatoire.");
        }
        String basename = suppliedName.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        String cleaned = basename
                .replaceAll("[\\p{Cntrl}/\\\\]", "_")
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_")
                .strip();
        if (cleaned.isBlank() || cleaned.length() > 255 || cleaned.startsWith(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du fichier est invalide.");
        }
        return cleaned;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Seuls les fichiers Markdown et PDF sont acceptés.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

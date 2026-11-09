package com.assistia.service.ingestion;

import com.assistia.entity.KnowledgeChunk;
import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeDocumentCategory;
import com.assistia.entity.KnowledgeDocumentVersion;
import com.assistia.entity.KnowledgeDocumentVersionId;
import com.assistia.entity.KnowledgeIndexingStatus;
import com.assistia.entity.User;
import com.assistia.repository.KnowledgeChunkRepository;
import com.assistia.repository.KnowledgeDocumentRepository;
import com.assistia.repository.KnowledgeDocumentVersionRepository;
import com.assistia.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentLifecycleService {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final UserRepository users;
    private final KnowledgeDocumentVersionRepository versions;

    public KnowledgeDocumentLifecycleService(
            KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks,
            UserRepository users,
            KnowledgeDocumentVersionRepository versions) {
        this.documents = documents;
        this.chunks = chunks;
        this.users = users;
        this.versions = versions;
    }

    @Transactional
    public StoredDocument begin(
            Long documentId,
            Long userId,
            String title,
            KnowledgeDocumentCategory category,
            String filename,
            String mimeType,
            long fileSize,
            String sha256,
            String extension) {
        var existingVersionHash = versions.findByContentSha256(sha256);
        var existingDocumentHash = documents.findByContentSha256(sha256);
        KnowledgeDocument document;
        KnowledgeDocumentVersion failedVersionToRetry = null;
        if (documentId == null) {
            if (existingVersionHash.isPresent() || existingDocumentHash.isPresent()) {
                throw duplicateDocument();
            }
            document = new KnowledgeDocument(title, "", category, null);
            document.setCurrentVersion(0);
            document.setIndexingVersion(1);
        } else {
            document = documents.findByIdForUpdate(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
            if (document.getArchivedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Le document est archivé.");
            }
            if (document.getIndexingStatus() == KnowledgeIndexingStatus.PROCESSING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Une version est déjà en cours de traitement.");
            }
            if (existingVersionHash.isPresent()) {
                KnowledgeDocumentVersion match = existingVersionHash.get();
                boolean retryable = match.getDocument().getId().equals(documentId)
                        && match.getId().getDocumentVersion().equals(document.getIndexingVersion())
                        && document.getIndexingStatus() == KnowledgeIndexingStatus.FAILED;
                if (!retryable) {
                    throw duplicateDocument();
                }
                failedVersionToRetry = match;
            }
            if (existingDocumentHash.isPresent()
                    && !existingDocumentHash.get().getId().equals(documentId)) {
                throw duplicateDocument();
            }
            boolean retrySameContent = document.getIndexingStatus() == KnowledgeIndexingStatus.FAILED
                    && existingDocumentHash.map(existing -> existing.getId().equals(documentId)).orElse(false);
            if (retrySameContent) {
                failedVersionToRetry = versions.findById(
                                new KnowledgeDocumentVersionId(documentId, document.getIndexingVersion()))
                        .orElse(null);
            } else {
                document.setIndexingVersion(Math.max(document.getCurrentVersion(), document.getIndexingVersion()) + 1);
            }
            if (title != null) {
                document.setTitle(title);
            }
            if (category != null) {
                document.setCategory(category);
            }
        }

        User importer = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable."));
        document.setOriginalFilename(filename);
        document.setMimeType(mimeType);
        document.setFileSizeBytes(fileSize);
        document.setContentSha256(sha256);
        document.setImportedAt(LocalDateTime.now());
        document.setImportedBy(importer);
        document.setIndexingStatus(KnowledgeIndexingStatus.PROCESSING);
        KnowledgeDocument saved = documents.saveAndFlush(document);
        saved.setStoragePath("knowledge/" + saved.getId() + "/"
                + saved.getIndexingVersion() + "/" + UUID.randomUUID() + extension);
        saved = documents.saveAndFlush(saved);
        if (failedVersionToRetry == null) {
            versions.saveAndFlush(new KnowledgeDocumentVersion(saved, saved.getIndexingVersion(),
                    filename, mimeType, fileSize, sha256, saved.getStoragePath()));
        } else {
            failedVersionToRetry.setOriginalFilename(filename);
            failedVersionToRetry.setMimeType(mimeType);
            failedVersionToRetry.setFileSizeBytes(fileSize);
            failedVersionToRetry.setStoragePath(saved.getStoragePath());
            versions.saveAndFlush(failedVersionToRetry);
        }
        return new StoredDocument(saved.getId(), saved.getIndexingVersion(), saved.getStoragePath());
    }

    @Transactional
    public void complete(StoredDocument candidate, List<ExtractedPage> pages, List<ChunkDraft> drafts) {
        KnowledgeDocument document = documents.findByIdForUpdate(candidate.documentId())
                .orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
        if (document.getIndexingStatus() != KnowledgeIndexingStatus.PROCESSING
                || !document.getIndexingVersion().equals(candidate.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La version candidate n'est plus active.");
        }
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Le document ne contient aucun fragment exploitable.");
        }
        String extractedText = pages.stream().map(ExtractedPage::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new IllegalArgumentException("Le document ne contient aucun texte exploitable."));
        for (int position = 0; position < drafts.size(); position++) {
            ChunkDraft draft = drafts.get(position);
            if (draft.position() != position || draft.content().isBlank() || draft.estimatedTokenCount() < 0) {
                throw new IllegalArgumentException("Les fragments du document sont incohérents.");
            }
            KnowledgeChunk chunk = new KnowledgeChunk(document, candidate.version(), draft.position(),
                    draft.content(), draft.estimatedTokenCount());
            chunk.setSourcePage(draft.sourcePage());
            chunk.setSourceStartOffset(draft.sourceStartOffset());
            chunk.setSourceEndOffset(draft.sourceEndOffset());
            chunks.save(chunk);
        }
        chunks.flush();
        if (document.getCurrentVersion() == 0) {
            document.setContent(extractedText);
        }
        document.setIndexingStatus(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        documents.save(document);
    }

    @Transactional
    public void fail(StoredDocument candidate) {
        documents.findByIdForUpdate(candidate.documentId()).ifPresent(document -> {
            if (document.getIndexingVersion().equals(candidate.version())
                    && document.getIndexingStatus() == KnowledgeIndexingStatus.PROCESSING) {
                document.setIndexingStatus(KnowledgeIndexingStatus.FAILED);
                documents.save(document);
            }
        });
    }

    @Transactional
    public KnowledgeDocument archive(Long id) {
        KnowledgeDocument document = documents.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
        if (document.getIndexingStatus() == KnowledgeIndexingStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le document ne peut pas être archivé pendant un import.");
        }
        document.setArchivedAt(LocalDateTime.now());
        return documents.save(document);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> findAll() {
        return documents.findAllByArchivedAtIsNullOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument findById(Long id) {
        return documents.findById(id).orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeChunk> findCurrentOrCandidateChunks(Long id, int version) {
        KnowledgeDocument document = findById(id);
        boolean available = version == document.getCurrentVersion()
                || (version == document.getIndexingVersion()
                && (document.getIndexingStatus() == KnowledgeIndexingStatus.AWAITING_EMBEDDINGS
                || document.getIndexingStatus() == KnowledgeIndexingStatus.READY_TO_PUBLISH));
        if (!available) {
            throw new EntityNotFoundException("Version documentaire introuvable.");
        }
        return chunks.findAllByDocumentIdAndDocumentVersionOrderByChunkPosition(id, version);
    }

    private static ResponseStatusException duplicateDocument() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Un document de contenu identique existe déjà.");
    }
}

package com.assistia.service;

import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeIndexingStatus;
import com.assistia.repository.KnowledgeChunkVectorRepository;
import com.assistia.repository.KnowledgeChunkVectorRepository.ChunkVector;
import com.assistia.repository.KnowledgeDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import microsoft.sql.Vector;
import microsoft.sql.Vector.VectorDimensionType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@ConditionalOnProperty(name = "assistia.rag.enabled", havingValue = "true")
public class KnowledgeDocumentEmbeddingService {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkVectorRepository chunks;
    private final ObjectProvider<EmbeddingProvider> providerProvider;
    private final String model;
    private final int dimensions;

    public KnowledgeDocumentEmbeddingService(
            KnowledgeDocumentRepository documents,
            KnowledgeChunkVectorRepository chunks,
            ObjectProvider<EmbeddingProvider> providerProvider,
            @Value("${assistia.embedding.model:gemini-embedding-2}") String model,
            @Value("${assistia.embedding.output-dimensions:1536}") int dimensions) {
        if (model == null || model.isBlank() || model.length() > 100 || dimensions != 1536) {
            throw new IllegalArgumentException("Embedding configuration is invalid.");
        }
        this.documents = documents;
        this.chunks = chunks;
        this.providerProvider = providerProvider;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void indexVersion(Long documentId, Integer version) {
        KnowledgeDocument document = documents.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
        validateCandidate(document, version);
        List<ChunkVector> chunkRows = chunks.findByDocumentAndVersion(documentId, version);
        if (chunkRows.isEmpty()) {
            throw new IllegalStateException("Candidate document version has no chunks.");
        }

        EmbeddingProvider provider = null;
        for (ChunkVector chunk : chunkRows) {
            if (isComplete(chunk)) {
                continue;
            }
            if (!isUnembedded(chunk)) {
                throw inconsistentEmbedding();
            }
            if (provider == null) {
                provider = providerProvider.getIfAvailable();
                if (provider == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Le fournisseur d'embeddings n'est pas configuré.");
                }
            }
            float[] vector = provider.embed(document.getTitle(), chunk.content());
            validateGenerated(vector);
            boolean saved = chunks.saveEmbedding(
                    chunk.id(), documentId, version, vector, model, dimensions);
            if (!saved) {
                ChunkVector current = findCurrentChunk(documentId, version, chunk.id());
                if (!isComplete(current)) {
                    throw inconsistentEmbedding();
                }
            }
        }

        List<ChunkVector> completed = chunks.findByDocumentAndVersion(documentId, version);
        if (completed.isEmpty() || completed.stream().anyMatch(row -> !isComplete(row))) {
            throw inconsistentEmbedding();
        }
        if (document.getIndexingStatus() == KnowledgeIndexingStatus.AWAITING_EMBEDDINGS
                && documents.markReadyToPublish(documentId, version, document.getCurrentVersion()) != 1) {
            KnowledgeDocument latest = documents.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("Document introuvable."));
            if (latest.getIndexingStatus() != KnowledgeIndexingStatus.READY_TO_PUBLISH
                    || !latest.getIndexingVersion().equals(version)
                    || !latest.getCurrentVersion().equals(document.getCurrentVersion())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La version candidate a changé pendant l'indexation.");
            }
        }
    }

    private ChunkVector findCurrentChunk(Long documentId, Integer version, long chunkId) {
        return chunks.findByDocumentAndVersion(documentId, version).stream()
                .filter(row -> row.id() == chunkId)
                .findFirst()
                .orElseThrow(() -> inconsistentEmbedding());
    }

    private void validateCandidate(KnowledgeDocument document, Integer version) {
        if (document.getArchivedAt() != null
                || !document.getIndexingVersion().equals(version)
                || document.getCurrentVersion().equals(version)
                || (document.getIndexingStatus() != KnowledgeIndexingStatus.AWAITING_EMBEDDINGS
                && document.getIndexingStatus() != KnowledgeIndexingStatus.READY_TO_PUBLISH)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La version demandée n'est pas une candidate indexable.");
        }
    }

    private boolean isComplete(ChunkVector chunk) {
        if (chunk.vector() == null && chunk.model() == null && chunk.dimension() == null) {
            return false;
        }
        if (chunk.vector() == null || !model.equals(chunk.model())
                || chunk.dimension() == null || chunk.dimension() != dimensions) {
            return false;
        }
        return isValidVector(chunk.vector());
    }

    private static boolean isUnembedded(ChunkVector chunk) {
        return chunk.vector() == null && chunk.model() == null && chunk.dimension() == null;
    }

    private boolean isValidVector(Vector vector) {
        if (vector.getDimensionCount() != dimensions
                || vector.getVectorDimensionType() != VectorDimensionType.FLOAT32
                || vector.getData() == null
                || vector.getData().length != dimensions) {
            return false;
        }
        for (Object value : vector.getData()) {
            if (!(value instanceof Float number) || !Float.isFinite(number)) {
                return false;
            }
        }
        return true;
    }

    private void validateGenerated(float[] vector) {
        if (vector == null || vector.length != dimensions) {
            throw new EmbeddingProviderException("Embedding provider returned an invalid dimension.", false);
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new EmbeddingProviderException("Embedding provider returned an invalid value.", false);
            }
        }
    }

    private static ResponseStatusException inconsistentEmbedding() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Les données d'embedding du fragment sont incohérentes.");
    }
}

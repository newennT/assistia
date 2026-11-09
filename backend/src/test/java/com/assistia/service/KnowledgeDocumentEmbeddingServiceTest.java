package com.assistia.service;

import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeIndexingStatus;
import com.assistia.repository.KnowledgeChunkVectorRepository;
import com.assistia.repository.KnowledgeChunkVectorRepository.ChunkVector;
import com.assistia.repository.KnowledgeDocumentRepository;
import microsoft.sql.Vector;
import microsoft.sql.Vector.VectorDimensionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentEmbeddingServiceTest {
    private static final String MODEL = "gemini-embedding-2";
    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeChunkVectorRepository chunks = mock(KnowledgeChunkVectorRepository.class);
    private final ObjectProvider<EmbeddingProvider> providerProvider = mock(ObjectProvider.class);
    private final EmbeddingProvider provider = mock(EmbeddingProvider.class);
    private final KnowledgeDocumentEmbeddingService service =
            new KnowledgeDocumentEmbeddingService(documents, chunks, providerProvider, MODEL, 1536);

    @Test
    void processesFragmentsInPositionOrderAndMarksReadyOnlyAfterAllAreStored() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        ChunkVector second = emptyChunk(2, 1, "second");
        ChunkVector first = emptyChunk(1, 0, "first");
        when(chunks.findByDocumentAndVersion(7L, 2))
                .thenReturn(List.of(first, second), List.of(complete(first), complete(second)));
        when(provider.embed("Test document", "first")).thenReturn(validVector());
        when(provider.embed("Test document", "second")).thenReturn(validVector());
        when(chunks.saveEmbedding(any(), eq(7L), eq(2), any(), eq(MODEL), eq(1536))).thenReturn(true);
        when(documents.markReadyToPublish(7L, 2, 1)).thenReturn(1);

        service.indexVersion(7L, 2);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(provider, chunks);
        order.verify(provider).embed("Test document", "first");
        order.verify(chunks).saveEmbedding(eq(1L), eq(7L), eq(2), any(float[].class), eq(MODEL), eq(1536));
        order.verify(provider).embed("Test document", "second");
        order.verify(chunks).saveEmbedding(eq(2L), eq(7L), eq(2), any(float[].class), eq(MODEL), eq(1536));
        verify(documents).markReadyToPublish(7L, 2, 1);
        verify(document, never()).setCurrentVersion(any());
    }

    @Test
    void resumesPartialIndexingWithoutRecomputingCompletedFragments() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        ChunkVector indexed = complete(emptyChunk(1, 0, "indexed"));
        ChunkVector missing = emptyChunk(2, 1, "missing");
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(chunks.findByDocumentAndVersion(7L, 2))
                .thenReturn(List.of(indexed, missing), List.of(indexed, complete(missing)));
        when(provider.embed("Test document", "missing")).thenReturn(validVector());
        when(chunks.saveEmbedding(2L, 7L, 2, validVector(), MODEL, 1536)).thenReturn(true);
        when(documents.markReadyToPublish(7L, 2, 1)).thenReturn(1);

        service.indexVersion(7L, 2);

        verify(provider, never()).embed("Test document", "indexed");
        verify(provider).embed("Test document", "missing");
    }

    @Test
    void doesNotOverwriteAnExistingCorrectEmbedding() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.READY_TO_PUBLISH);
        ChunkVector existing = complete(emptyChunk(1, 0, "already indexed"));
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentAndVersion(7L, 2)).thenReturn(List.of(existing), List.of(existing));

        service.indexVersion(7L, 2);

        verify(chunks, never()).saveEmbedding(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(providerProvider, never()).getIfAvailable();
        verify(documents, never()).markReadyToPublish(any(), any(), any());
    }

    @Test
    void rejectsInconsistentMetadataWithoutCallingProvider() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        ChunkVector invalid = new ChunkVector(1, 0, "fragment", vector(1.0f), MODEL, null);
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentAndVersion(7L, 2)).thenReturn(List.of(invalid));

        assertThatThrownBy(() -> service.indexVersion(7L, 2))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(providerProvider, never()).getIfAvailable();
        verify(documents, never()).markReadyToPublish(any(), any(), any());
    }

    @Test
    void doesNotMarkVersionReadyWhenProviderFailsAndNeverChangesPublishedVersion() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        ChunkVector missing = emptyChunk(1, 0, "fragment");
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentAndVersion(7L, 2)).thenReturn(List.of(missing));
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.embed("Test document", "fragment"))
                .thenThrow(new EmbeddingProviderException("temporary", true));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.indexVersion(7L, 2))
                .isInstanceOf(EmbeddingProviderException.class);
        verify(documents, never()).markReadyToPublish(any(), any(), any());
        verify(document, never()).setCurrentVersion(any());
    }

    @Test
    void rejectsIndexingWhenRagHasNoEmbeddingProvider() {
        KnowledgeDocument document = candidate(KnowledgeIndexingStatus.AWAITING_EMBEDDINGS);
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentAndVersion(7L, 2)).thenReturn(List.of(emptyChunk(1, 0, "fragment")));
        when(providerProvider.getIfAvailable()).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.indexVersion(7L, 2))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static KnowledgeDocument candidate(KnowledgeIndexingStatus status) {
        KnowledgeDocument document = mock(KnowledgeDocument.class);
        when(document.getId()).thenReturn(7L);
        when(document.getTitle()).thenReturn("Test document");
        when(document.getIndexingVersion()).thenReturn(2);
        when(document.getCurrentVersion()).thenReturn(1);
        when(document.getIndexingStatus()).thenReturn(status);
        when(document.getArchivedAt()).thenReturn(null);
        return document;
    }

    private static ChunkVector emptyChunk(long id, int position, String content) {
        return new ChunkVector(id, position, content, null, null, null);
    }

    private static ChunkVector complete(ChunkVector row) {
        return new ChunkVector(row.id(), row.position(), row.content(), vector(0.5f), MODEL, 1536);
    }

    private static float[] validVector() {
        return new float[1536];
    }

    private static Vector vector(float value) {
        Float[] values = new Float[1536];
        java.util.Arrays.fill(values, value);
        return new Vector(1536, VectorDimensionType.FLOAT32, values);
    }
}

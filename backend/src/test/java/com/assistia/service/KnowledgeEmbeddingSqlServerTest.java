package com.assistia.service;

import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeDocumentCategory;
import com.assistia.repository.KnowledgeChunkRepository;
import com.assistia.repository.KnowledgeChunkVectorRepository;
import com.assistia.repository.KnowledgeDocumentRepository;
import microsoft.sql.Vector;
import microsoft.sql.Vector.VectorDimensionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Opt-in only; operates on the disposable AssistIAV6Test SQL Server 2025 database. */
@SpringBootTest(properties = "assistia.rag.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("sqlserver-v6")
@EnabledIfEnvironmentVariable(named = "ASSISTIA_SQLSERVER_V6_TESTS", matches = "true")
class KnowledgeEmbeddingSqlServerTest {
    private static final String MODEL = "gemini-embedding-2";

    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeChunkVectorRepository vectors;
    @Autowired KnowledgeDocumentEmbeddingService embeddingService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean(name = "geminiEmbeddingProvider")
    EmbeddingProvider provider;

    @DynamicPropertySource
    static void protectMainDatabase(DynamicPropertyRegistry registry) {
        String configuredUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = System.getenv("ASSISTIA_SQLSERVER_V6_TEST_URL");
        }
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = "jdbc:sqlserver://localhost:1434;databaseName=AssistIAV6Test;"
                    + "encrypt=true;trustServerCertificate=true;vectorTypeSupport=v2";
        }
        long matchingDatabaseParameters = java.util.Arrays.stream(configuredUrl.split(";"))
                .filter(parameter -> parameter.trim().regionMatches(true, 0, "databaseName=", 0, 13))
                .count();
        boolean validTestDatabase = matchingDatabaseParameters == 1
                && java.util.Arrays.stream(configuredUrl.split(";"))
                .filter(parameter -> parameter.trim().regionMatches(true, 0, "databaseName=", 0, 13))
                .map(parameter -> parameter.trim().substring(13))
                .allMatch("AssistIAV6Test"::equalsIgnoreCase);
        if (!validTestDatabase) {
            throw new IllegalStateException(
                    "Embedding SQL integration tests may only target the AssistIAV6Test database.");
        }
        String safeUrl = configuredUrl;
        registry.add("spring.datasource.url", () -> safeUrl);
    }

    @Test
    void storesVectorsResumesWithoutOverwriteAndLeavesPublishedVersionUnchanged() {
        KnowledgeDocument document = candidate();
        var published = chunks.saveAndFlush(new com.assistia.entity.KnowledgeChunk(
                document, 1, 0, "Synthetic published fragment " + UUID.randomUUID(), 4));
        var first = chunks.saveAndFlush(new com.assistia.entity.KnowledgeChunk(
                document, 2, 0, "Synthetic first fragment " + UUID.randomUUID(), 4));
        var second = chunks.saveAndFlush(new com.assistia.entity.KnowledgeChunk(
                document, 2, 1, "Synthetic second fragment " + UUID.randomUUID(), 4));
        assertThat(vectors.findByDocumentAndVersion(document.getId(), 2))
                .allSatisfy(row -> {
                    assertThat(row.vector()).isNull();
                    assertThat(row.model()).isNull();
                    assertThat(row.dimension()).isNull();
                });
        when(provider.embed(anyString(), anyString())).thenReturn(vectorValues(0.25f));

        embeddingService.indexVersion(document.getId(), 2);

        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("READY_TO_PUBLISH");
        assertThat(jdbc.queryForObject("SELECT current_version FROM dbo.knowledge_documents WHERE id=?",
                Integer.class, document.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("Published content");
        assertThat(jdbc.queryForObject("SELECT content FROM dbo.knowledge_chunks WHERE id=?",
                String.class, published.getId())).startsWith("Synthetic published fragment ");
        assertVectorStored(first.getId(), 0.25f);
        assertVectorStored(second.getId(), 0.25f);

        reset(provider);
        embeddingService.indexVersion(document.getId(), 2);
        verify(provider, never()).embed(anyString(), anyString());

        assertThat(vectors.saveEmbedding(first.getId(), document.getId(), 2,
                vectorValues(0.75f), MODEL, 1536)).isFalse();
        assertVectorStored(first.getId(), 0.25f);
    }

    @Test
    void detectsVectorMetadataMismatchAndDoesNotAdvanceStatus() {
        KnowledgeDocument document = candidate();
        var chunk = chunks.saveAndFlush(new com.assistia.entity.KnowledgeChunk(
                document, 2, 0, "Synthetic metadata test " + UUID.randomUUID(), 4));
        assertThat(vectors.saveEmbedding(chunk.getId(), document.getId(), 2,
                vectorValues(0.5f), MODEL, 1536)).isTrue();
        assertVectorStored(chunk.getId(), 0.5f);
        jdbc.update("UPDATE dbo.knowledge_chunks SET embedding_model=NULL WHERE id=?", chunk.getId());

        assertThatThrownBy(() -> embeddingService.indexVersion(document.getId(), 2))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("AWAITING_EMBEDDINGS");
        verify(provider, never()).embed(anyString(), anyString());
    }

    @Test
    void writesNothingWhenProviderReturnsAnInvalidVector() {
        KnowledgeDocument document = candidate();
        var chunk = chunks.saveAndFlush(new com.assistia.entity.KnowledgeChunk(
                document, 2, 0, "Synthetic invalid vector " + UUID.randomUUID(), 4));
        when(provider.embed(anyString(), anyString())).thenReturn(new float[1535]);

        assertThatThrownBy(() -> embeddingService.indexVersion(document.getId(), 2))
                .isInstanceOf(EmbeddingProviderException.class);
        assertThat(jdbc.queryForObject("SELECT embedding FROM dbo.knowledge_chunks WHERE id=?",
                Object.class, chunk.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("AWAITING_EMBEDDINGS");
    }

    private KnowledgeDocument candidate() {
        String marker = UUID.randomUUID().toString();
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(
                "Embedding test " + marker, "Published content", KnowledgeDocumentCategory.GENERAL, null));
        jdbc.update("UPDATE dbo.knowledge_documents SET current_version=1,indexing_version=2," +
                "indexing_status='AWAITING_EMBEDDINGS' WHERE id=?", document.getId());
        return documents.findById(document.getId()).orElseThrow();
    }

    private void assertVectorStored(Long chunkId, float expectedValue) {
        Vector vector = jdbc.queryForObject(
                "SELECT embedding FROM dbo.knowledge_chunks WHERE id=?",
                (result, row) -> result.getObject("embedding", Vector.class), chunkId);
        assertThat(vector).isNotNull();
        assertThat(vector.getDimensionCount()).isEqualTo(1536);
        assertThat(vector.getVectorDimensionType()).isEqualTo(VectorDimensionType.FLOAT32);
        assertThat(vector.getData()).hasSize(1536);
        assertThat(vector.getData()[0]).isEqualTo(expectedValue);
        assertThat(jdbc.queryForObject(
                "SELECT embedding_model FROM dbo.knowledge_chunks WHERE id=?", String.class, chunkId)).isEqualTo(MODEL);
        assertThat(jdbc.queryForObject(
                "SELECT embedding_dimension FROM dbo.knowledge_chunks WHERE id=?", Integer.class, chunkId))
                .isEqualTo(1536);
    }

    private static float[] vectorValues(float value) {
        float[] vector = new float[1536];
        java.util.Arrays.fill(vector, value);
        return vector;
    }
}

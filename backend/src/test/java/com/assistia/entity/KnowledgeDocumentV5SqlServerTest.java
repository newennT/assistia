package com.assistia.entity;

import com.assistia.repository.KnowledgeChunkRepository;
import com.assistia.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in only; configure a disposable SQL Server 2025 database named AssistIAV5Test. */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("sqlserver-v5")
@EnabledIfEnvironmentVariable(named = "ASSISTIA_SQLSERVER_V5_TESTS", matches = "true")
class KnowledgeDocumentV5SqlServerTest {

    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired JdbcTemplate jdbc;
    @Test
    void flywayV1ThroughV5AndJpaValidateAreActive() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1", Integer.class))
                .isEqualTo(5);
        String type = jdbc.queryForObject("SELECT TYPE_NAME(user_type_id) FROM sys.columns " +
                "WHERE object_id = OBJECT_ID('dbo.knowledge_chunks') AND name = 'embedding'", String.class);
        assertThat(type).isEqualTo("vector");
        Integer maxDimensions = jdbc.queryForObject("SELECT vector_dimensions FROM sys.columns " +
                "WHERE object_id = OBJECT_ID('dbo.knowledge_chunks') AND name = 'embedding'", Integer.class);
        assertThat(maxDimensions).isEqualTo(1536);
    }

    @Test
    void backfillsLegacyDocumentMetadataWithoutChangingContent() {
        var rows = jdbc.queryForList("SELECT content, current_version, indexing_version, indexing_status, imported_at, " +
                "imported_by_id FROM dbo.knowledge_documents WHERE source_reference='legacy-source' " +
                "AND content='legacy content must remain byte-for-byte intact'");
        Assumptions.assumeTrue(!rows.isEmpty(), "seed a legacy row after V4 to exercise the V5 backfill");
        var row = rows.getFirst();
        assertThat(row.get("content")).isEqualTo("legacy content must remain byte-for-byte intact");
        assertThat(row.get("current_version")).isEqualTo(1);
        assertThat(row.get("indexing_version")).isEqualTo(1);
        assertThat(row.get("indexing_status")).isEqualTo("PENDING");
        assertThat(row.get("imported_at")).isNotNull();
        assertThat(row.get("imported_by_id")).isNull();
    }

    @Test
    void keepsMultipleVersionsAndAllowsChunksBeforePromotionWithNullEmbedding() {
        String marker = UUID.randomUUID().toString();
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(
                "Doc " + marker, "existing content", KnowledgeDocumentCategory.GENERAL, "source"));
        jdbc.update("UPDATE dbo.knowledge_documents SET current_version=1, indexing_version=2, " +
                "indexing_status='PROCESSING' WHERE id=?", document.getId());

        KnowledgeChunk v1 = chunks.saveAndFlush(new KnowledgeChunk(document, 1, 0, "v1 chunk", 2));
        KnowledgeChunk v2 = chunks.saveAndFlush(new KnowledgeChunk(document, 2, 0, "v2 chunk", 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.knowledge_chunks WHERE document_id=?", Integer.class,
                document.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT embedding FROM dbo.knowledge_chunks WHERE id=?", Object.class, v2.getId()))
                .isNull();

        assertThatThrownBy(() -> chunks.saveAndFlush(new KnowledgeChunk(document, 2, 0, "duplicate", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dbo.knowledge_chunks " +
                "(document_id,document_version,chunk_position,content,estimated_token_count) VALUES (-1,3,0,'bad',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

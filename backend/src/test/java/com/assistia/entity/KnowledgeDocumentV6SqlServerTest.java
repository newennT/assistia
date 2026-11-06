package com.assistia.entity;

import com.assistia.repository.KnowledgeChunkRepository;
import com.assistia.repository.KnowledgeDocumentRepository;
import com.assistia.repository.UserRepository;
import com.assistia.service.ingestion.ChunkDraft;
import com.assistia.service.ingestion.ExtractedPage;
import com.assistia.service.ingestion.KnowledgeDocumentLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in only; configure a disposable SQL Server 2025 database named AssistIAV6Test. */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("sqlserver-v6")
@EnabledIfEnvironmentVariable(named = "ASSISTIA_SQLSERVER_V6_TESTS", matches = "true")
class KnowledgeDocumentV6SqlServerTest {

    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired UserRepository users;
    @Autowired KnowledgeDocumentLifecycleService lifecycle;
    @Autowired JdbcTemplate jdbc;
    @Test
    void flywayV1ThroughV6AndJpaValidateAreActive() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1", Integer.class))
                .isEqualTo(6);
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
    void keepsPublishedVersionWhilePreparingChunksWithoutEmbedding() {
        String marker = UUID.randomUUID().toString();
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(
                "Doc " + marker, "existing content", KnowledgeDocumentCategory.GENERAL, "source"));
        jdbc.update("UPDATE dbo.knowledge_documents SET current_version=1, indexing_version=2, " +
                "indexing_status='PROCESSING' WHERE id=?", document.getId());

        KnowledgeChunk v1 = chunks.saveAndFlush(new KnowledgeChunk(document, 1, 0, "v1 chunk", 2));
        KnowledgeChunk v2 = chunks.saveAndFlush(new KnowledgeChunk(document, 2, 0, "v2 chunk", 2));
        jdbc.update("UPDATE dbo.knowledge_documents SET indexing_status='AWAITING_EMBEDDINGS' WHERE id=?",
                document.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.knowledge_chunks WHERE document_id=?", Integer.class,
                document.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_version FROM dbo.knowledge_documents WHERE id=?",
                Integer.class, document.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("AWAITING_EMBEDDINGS");
        assertThat(jdbc.queryForObject("SELECT embedding FROM dbo.knowledge_chunks WHERE id=?", Object.class, v2.getId()))
                .isNull();

        assertThatThrownBy(() -> chunks.saveAndFlush(new KnowledgeChunk(document, 2, 0, "duplicate", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dbo.knowledge_chunks " +
                "(document_id,document_version,chunk_position,content,estimated_token_count) VALUES (-1,3,0,'bad',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v6StoresPrivateStorageKeyAndRejectsDuplicateContentHashes() {
        String marker = UUID.randomUUID().toString();
        KnowledgeDocument first = new KnowledgeDocument("Hash " + marker, "content",
                KnowledgeDocumentCategory.GENERAL, null);
        first.setContentSha256("a".repeat(64));
        first.setStoragePath("knowledge/private/" + marker + ".md");
        documents.saveAndFlush(first);
        assertThat(jdbc.queryForObject("SELECT storage_path FROM dbo.knowledge_documents WHERE id=?",
                String.class, first.getId())).isEqualTo(first.getStoragePath());

        KnowledgeDocument duplicate = new KnowledgeDocument("Hash duplicate " + marker, "content",
                KnowledgeDocumentCategory.GENERAL, null);
        duplicate.setContentSha256(first.getContentSha256());
        assertThatThrownBy(() -> documents.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lifecycleFailureRollsBackPartialChunksAndRetryKeepsPublishedVersion() {
        var user = users.saveAndFlush(new User(
                "ingestion-" + UUID.randomUUID() + "@assistia.local",
                "Test", "Technician", UserRole.TECHNICIAN, "IT"));
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(
                "Versioned " + UUID.randomUUID(), "published v1", KnowledgeDocumentCategory.GENERAL, null));
        jdbc.update("UPDATE dbo.knowledge_documents SET current_version=1,indexing_version=1," +
                "indexing_status='INDEXED' WHERE id=?", document.getId());
        chunks.saveAndFlush(new KnowledgeChunk(document, 1, 0, "published v1 chunk", 4));
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);

        var candidate = lifecycle.begin(document.getId(), user.getId(), null, null,
                "candidate.md", "text/markdown", 4, hash, ".md");
        assertThat(candidate.version()).isEqualTo(2);
        assertThatThrownBy(() -> lifecycle.complete(candidate,
                List.of(new ExtractedPage(null, "candidate content")),
                List.of(new ChunkDraft(0, "valid first chunk", 4, null, null, null),
                        new ChunkDraft(1, " ", 0, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(chunks.countByDocumentIdAndDocumentVersion(document.getId(), 2)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_version FROM dbo.knowledge_documents WHERE id=?",
                Integer.class, document.getId())).isEqualTo(1);
        lifecycle.fail(candidate);

        var retry = lifecycle.begin(document.getId(), user.getId(), null, null,
                "candidate.md", "text/markdown", 4, hash, ".md");
        assertThat(retry.version()).isEqualTo(2);
        lifecycle.fail(retry);
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("FAILED");
        assertThat(chunks.countByDocumentIdAndDocumentVersion(document.getId(), 2)).isZero();
    }

    @Test
    void successfulImportLeavesNewVersionAwaitingEmbeddingsAndKeepsOldVersionCurrent() {
        var user = users.saveAndFlush(new User(
                "ready-" + UUID.randomUUID() + "@assistia.local",
                "Test", "Technician", UserRole.TECHNICIAN, "IT"));
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(
                "Ready " + UUID.randomUUID(), "published v1", KnowledgeDocumentCategory.GENERAL, null));
        jdbc.update("UPDATE dbo.knowledge_documents SET current_version=1,indexing_version=1," +
                "indexing_status='INDEXED' WHERE id=?", document.getId());
        chunks.saveAndFlush(new KnowledgeChunk(document, 1, 0, "published v1 chunk", 4));
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        var candidate = lifecycle.begin(document.getId(), user.getId(), null, null,
                "candidate.md", "text/markdown", 4, hash, ".md");

        lifecycle.complete(candidate, List.of(new ExtractedPage(3, "extracted page text")),
                List.of(new ChunkDraft(0, "new page chunk", 4, 3, 0L, 16L)));

        assertThat(jdbc.queryForObject("SELECT current_version FROM dbo.knowledge_documents WHERE id=?",
                Integer.class, document.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT indexing_version FROM dbo.knowledge_documents WHERE id=?",
                Integer.class, document.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM dbo.knowledge_documents WHERE id=?",
                String.class, document.getId())).isEqualTo("AWAITING_EMBEDDINGS");
        assertThat(jdbc.queryForObject("SELECT source_page FROM dbo.knowledge_chunks " +
                "WHERE document_id=? AND document_version=2", Integer.class, document.getId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT embedding FROM dbo.knowledge_chunks " +
                "WHERE document_id=? AND document_version=2", Object.class, document.getId())).isNull();
    }

    @Test
    void duplicateSha256IsRejectedAcrossDifferentFilenames() {
        var user = users.saveAndFlush(new User(
                "duplicate-" + UUID.randomUUID() + "@assistia.local",
                "Test", "Technician", UserRole.TECHNICIAN, "IT"));
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        var first = lifecycle.begin(null, user.getId(), "First name", KnowledgeDocumentCategory.GENERAL,
                "first.md", "text/markdown", 10, hash, ".md");
        lifecycle.fail(first);

        assertThatThrownBy(() -> lifecycle.begin(null, user.getId(), "Different name",
                KnowledgeDocumentCategory.GENERAL, "different-name.pdf", "application/pdf", 10, hash, ".pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}

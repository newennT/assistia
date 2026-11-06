ALTER TABLE dbo.knowledge_documents
    ADD storage_path NVARCHAR(512) NULL,
        archived_at DATETIME2 NULL;

ALTER TABLE dbo.knowledge_documents DROP CONSTRAINT ck_kd_current_version_positive;
ALTER TABLE dbo.knowledge_documents
    ADD CONSTRAINT ck_kd_current_version_nonnegative CHECK (current_version >= 0);

ALTER TABLE dbo.knowledge_documents DROP CONSTRAINT ck_kd_indexing_status;
ALTER TABLE dbo.knowledge_documents
    ADD CONSTRAINT ck_kd_indexing_status CHECK
        (indexing_status IN ('PENDING', 'PROCESSING', 'AWAITING_EMBEDDINGS', 'INDEXED', 'FAILED', 'OUTDATED'));

CREATE UNIQUE INDEX uq_kd_content_sha256
    ON dbo.knowledge_documents (content_sha256)
    WHERE content_sha256 IS NOT NULL;

CREATE TABLE dbo.knowledge_document_versions (
    document_id BIGINT NOT NULL,
    document_version INT NOT NULL,
    original_filename NVARCHAR(255) NOT NULL,
    mime_type NVARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    storage_path NVARCHAR(512) NOT NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT df_kdv_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_knowledge_document_versions PRIMARY KEY (document_id, document_version),
    CONSTRAINT uq_kdv_content_sha256 UNIQUE (content_sha256),
    CONSTRAINT fk_kdv_document FOREIGN KEY (document_id)
        REFERENCES dbo.knowledge_documents(id),
    CONSTRAINT ck_kdv_document_version_positive CHECK (document_version > 0),
    CONSTRAINT ck_kdv_file_size_nonnegative CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_kdv_content_sha256 CHECK
        (LEN(content_sha256) = 64 AND content_sha256 NOT LIKE '%[^0-9a-fA-F]%')
);

CREATE INDEX idx_kdv_document_version
    ON dbo.knowledge_document_versions (document_id, document_version);

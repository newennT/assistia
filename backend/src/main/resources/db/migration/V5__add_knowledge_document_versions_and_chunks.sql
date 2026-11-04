ALTER TABLE dbo.knowledge_documents
    ADD original_filename NVARCHAR(255) NULL,
        mime_type NVARCHAR(100) NULL,
        file_size_bytes BIGINT NULL,
        content_sha256 VARCHAR(64) NULL,
        current_version INT NOT NULL
            CONSTRAINT df_kd_current_version DEFAULT 1,
        indexing_version INT NOT NULL
            CONSTRAINT df_kd_indexing_version DEFAULT 1,
        indexing_status NVARCHAR(20) NOT NULL
            CONSTRAINT df_kd_indexing_status DEFAULT 'PENDING',
        imported_at DATETIME2 NULL,
        imported_by_id BIGINT NULL;

EXEC('UPDATE dbo.knowledge_documents SET imported_at = created_at');

EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT ck_kd_current_version_positive CHECK (current_version > 0)');
EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT ck_kd_indexing_version_positive CHECK (indexing_version > 0)');
EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT ck_kd_indexing_status CHECK (indexing_status IN (''PENDING'', ''PROCESSING'', ''INDEXED'', ''FAILED'', ''OUTDATED''))');
EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT ck_kd_content_sha256 CHECK (content_sha256 IS NULL OR (LEN(content_sha256) = 64 AND content_sha256 NOT LIKE ''%[^0-9a-fA-F]%''))');
EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT ck_kd_file_size_nonnegative CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0)');
EXEC('ALTER TABLE dbo.knowledge_documents ADD CONSTRAINT fk_kd_imported_by FOREIGN KEY (imported_by_id) REFERENCES dbo.users(id)');

EXEC('CREATE INDEX idx_kd_indexing_status ON dbo.knowledge_documents (indexing_status, indexing_version)');
EXEC('CREATE INDEX idx_kd_imported_by ON dbo.knowledge_documents (imported_by_id)');

CREATE TABLE dbo.knowledge_chunks (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_knowledge_chunks PRIMARY KEY,
    document_id BIGINT NOT NULL,
    document_version INT NOT NULL,
    chunk_position INT NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    estimated_token_count INT NOT NULL,
    source_page INT NULL,
    source_start_offset BIGINT NULL,
    source_end_offset BIGINT NULL,
    embedding VECTOR(1536) NULL,
    embedding_model NVARCHAR(100) NULL,
    embedding_dimension INT NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT df_kc_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL CONSTRAINT df_kc_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_knowledge_chunks_document FOREIGN KEY (document_id)
        REFERENCES dbo.knowledge_documents(id),
    CONSTRAINT uq_knowledge_chunks_document_version_position
        UNIQUE (document_id, document_version, chunk_position),
    CONSTRAINT ck_kc_document_version_positive CHECK (document_version > 0),
    CONSTRAINT ck_kc_position_nonnegative CHECK (chunk_position >= 0),
    CONSTRAINT ck_kc_token_count_nonnegative CHECK (estimated_token_count >= 0),
    CONSTRAINT ck_kc_source_page_positive CHECK (source_page IS NULL OR source_page > 0),
    CONSTRAINT ck_kc_offsets_ordered CHECK
        (source_start_offset IS NULL OR source_end_offset IS NULL OR source_end_offset >= source_start_offset),
    CONSTRAINT ck_kc_embedding_dimension CHECK
        (embedding_dimension IS NULL OR embedding_dimension = 1536)
);

CREATE INDEX idx_kc_document_version ON dbo.knowledge_chunks (document_id, document_version, chunk_position);

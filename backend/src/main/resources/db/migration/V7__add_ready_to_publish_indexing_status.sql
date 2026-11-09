ALTER TABLE dbo.knowledge_documents DROP CONSTRAINT ck_kd_indexing_status;

ALTER TABLE dbo.knowledge_documents
    ADD CONSTRAINT ck_kd_indexing_status CHECK
        (indexing_status IN ('PENDING', 'PROCESSING', 'AWAITING_EMBEDDINGS',
                             'READY_TO_PUBLISH', 'INDEXED', 'FAILED', 'OUTDATED'));

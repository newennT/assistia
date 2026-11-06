package com.assistia.repository;

import com.assistia.entity.KnowledgeDocumentVersion;
import com.assistia.entity.KnowledgeDocumentVersionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeDocumentVersionRepository
        extends JpaRepository<KnowledgeDocumentVersion, KnowledgeDocumentVersionId> {
    Optional<KnowledgeDocumentVersion> findByContentSha256(String contentSha256);
}

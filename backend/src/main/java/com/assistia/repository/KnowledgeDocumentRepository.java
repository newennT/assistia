package com.assistia.repository;

import com.assistia.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    boolean existsByContentSha256(String contentSha256);

    Optional<KnowledgeDocument> findByContentSha256(String contentSha256);

    List<KnowledgeDocument> findAllByArchivedAtIsNullOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from KnowledgeDocument document where document.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update KnowledgeDocument document
            set document.indexingStatus = com.assistia.entity.KnowledgeIndexingStatus.READY_TO_PUBLISH,
                document.updatedAt = CURRENT_TIMESTAMP
            where document.id = :id
              and document.indexingVersion = :version
              and document.currentVersion = :currentVersion
              and document.indexingStatus = com.assistia.entity.KnowledgeIndexingStatus.AWAITING_EMBEDDINGS
            """)
    int markReadyToPublish(
            @Param("id") Long id,
            @Param("version") Integer version,
            @Param("currentVersion") Integer currentVersion);
}

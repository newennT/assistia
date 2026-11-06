package com.assistia.repository;

import com.assistia.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

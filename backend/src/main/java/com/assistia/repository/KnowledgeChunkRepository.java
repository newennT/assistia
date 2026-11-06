package com.assistia.repository;

import com.assistia.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findAllByDocumentIdAndDocumentVersionOrderByChunkPosition(Long documentId, Integer documentVersion);

    long countByDocumentIdAndDocumentVersion(Long documentId, Integer documentVersion);
}

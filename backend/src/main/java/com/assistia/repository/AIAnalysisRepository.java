package com.assistia.repository;

import com.assistia.entity.AIAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, Long> {
    List<AIAnalysis> findAllByTicket_IdOrderByCreatedAtDesc(Long ticketId);
}

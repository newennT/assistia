package com.assistia.controller;

import com.assistia.dto.knowledge.KnowledgeChunkResponse;
import com.assistia.dto.knowledge.KnowledgeDocumentResponse;
import com.assistia.entity.KnowledgeDocumentCategory;
import com.assistia.service.KnowledgeDocumentIngestionService;
import com.assistia.service.ingestion.KnowledgeDocumentLifecycleService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentIngestionService ingestion;
    private final KnowledgeDocumentLifecycleService lifecycle;

    public KnowledgeDocumentController(
            KnowledgeDocumentIngestionService ingestion,
            KnowledgeDocumentLifecycleService lifecycle) {
        this.ingestion = ingestion;
        this.lifecycle = lifecycle;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentResponse> importDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) KnowledgeDocumentCategory category,
            Authentication authentication) {
        KnowledgeDocumentResponse response = ingestion.importDocument(
                file, documentId, title, category,
                ((com.assistia.security.AssistiaPrincipal) authentication.getPrincipal()).id());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri()).body(response);
    }

    @GetMapping
    public List<KnowledgeDocumentResponse> listDocuments() {
        return lifecycle.findAll().stream().map(KnowledgeDocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentResponse getDocument(@PathVariable @Positive Long id) {
        return KnowledgeDocumentResponse.from(lifecycle.findById(id));
    }

    @GetMapping("/{id}/versions/{version}/chunks")
    public List<KnowledgeChunkResponse> getChunks(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Integer version) {
        return lifecycle.findCurrentOrCandidateChunks(id, version).stream()
                .map(KnowledgeChunkResponse::from)
                .toList();
    }

    @PostMapping("/{id}/archive")
    public KnowledgeDocumentResponse archive(@PathVariable @Positive Long id) {
        return KnowledgeDocumentResponse.from(lifecycle.archive(id));
    }
}

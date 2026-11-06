package com.assistia.service.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {
    private final DocumentChunkingService chunking =
            new DocumentChunkingService(new ApproximateTokenEstimator(), 120, 25);

    @Test
    void preservesHeadingsAndCodeBlocksForShortMarkdown() {
        String markdown = """
                # VPN troubleshooting

                Check the network connection first.

                ```bash
                ip route
                ```
                """;

        List<ChunkDraft> chunks = chunking.chunk(List.of(new ExtractedPage(null, markdown)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).contains("# VPN troubleshooting", "Check the network")
                .contains("```bash", "ip route", "```");
        assertThat(chunks.getFirst().estimatedTokenCount()).isPositive();
        assertThat(chunks.getFirst().sourcePage()).isNull();
    }

    @Test
    void splitsLongDocumentsInOrderAndAssignsContinuousPositions() {
        String content = IntStream.range(0, 40)
                .mapToObj(index -> "## Étape " + index + "\n\n" + "Résoudre le problème réseau. ".repeat(18))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow();

        List<ChunkDraft> chunks = chunking.chunk(List.of(new ExtractedPage(null, content)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(ChunkDraft::position)
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(String.join("\n", chunks.stream().map(ChunkDraft::content).toList()))
                .contains("## Étape 0", "## Étape 39");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.estimatedTokenCount()).isPositive());
    }

    @Test
    void keepsPdfPagesAndTheirOrderOnChunks() {
        List<ChunkDraft> chunks = chunking.chunk(List.of(
                new ExtractedPage(1, "Page une avec du texte."),
                new ExtractedPage(2, "Page deux avec du texte.")));

        assertThat(chunks).extracting(ChunkDraft::position).containsExactly(0, 1);
        assertThat(chunks).extracting(ChunkDraft::sourcePage).containsExactly(1, 2);
    }

    @Test
    void carriesSemanticContextAcrossLongParagraphChunks() {
        DocumentChunkingService small = new DocumentChunkingService(
                new ApproximateTokenEstimator(), 70, 25);
        String paragraph = IntStream.range(0, 40)
                .mapToObj(index -> "Sentence " + index + " explains the network recovery procedure clearly.")
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();

        List<ChunkDraft> chunks = small.chunk(List.of(new ExtractedPage(null, paragraph)));

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int index = 1; index < chunks.size(); index++) {
            HashSet<String> sharedWords = new HashSet<>(
                    Arrays.asList(chunks.get(index - 1).content().split("\\s+")));
            sharedWords.retainAll(Arrays.asList(chunks.get(index).content().split("\\s+")));
            assertThat(sharedWords).hasSizeGreaterThanOrEqualTo(4);
        }
    }
}

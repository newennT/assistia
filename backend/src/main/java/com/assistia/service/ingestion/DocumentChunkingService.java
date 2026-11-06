package com.assistia.service.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DocumentChunkingService {
    private static final int MINIMUM_TOKENS = 40;
    private final TokenEstimator tokenEstimator;
    private final int targetTokens;
    private final int overlapTokens;

    public DocumentChunkingService(
            TokenEstimator tokenEstimator,
            @Value("${assistia.knowledge.chunk-target-tokens:700}") int targetTokens,
            @Value("${assistia.knowledge.chunk-overlap-tokens:100}") int overlapTokens) {
        if (targetTokens < MINIMUM_TOKENS || overlapTokens < 0 || overlapTokens >= targetTokens) {
            throw new IllegalArgumentException("La configuration du découpage documentaire est invalide.");
        }
        this.tokenEstimator = tokenEstimator;
        this.targetTokens = targetTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<ChunkDraft> chunk(List<ExtractedPage> pages) {
        List<ChunkDraft> result = new ArrayList<>();
        for (ExtractedPage page : pages) {
            List<String> blocks = semanticBlocks(page.content());
            appendPageChunks(blocks, page.pageNumber(), result);
        }
        return List.copyOf(result);
    }

    private void appendPageChunks(List<String> blocks, Integer page, List<ChunkDraft> result) {
        List<String> pending = new ArrayList<>();
        for (String block : blocks) {
            List<String> pieces = splitOversizedBlock(block);
            for (String piece : pieces) {
                String candidate = join(pending, piece);
                if (!pending.isEmpty() && tokenEstimator.estimate(candidate) > targetTokens) {
                    addChunk(pending, page, result);
                    int overlapBudget = Math.min(overlapTokens,
                                Math.max(0, targetTokens - tokenEstimator.estimate(piece) - 1));
                    pending = overlapTail(pending, overlapBudget);
                }
                pending.add(piece);
            }
        }
        if (!pending.isEmpty()) {
            if (!result.isEmpty() && Objects.equals(result.getLast().sourcePage(), page)
                    && tokenEstimator.estimate(join(pending)) < MINIMUM_TOKENS
                    && tokenEstimator.estimate(result.getLast().content() + "\n\n" + join(pending)) <= targetTokens) {
                ChunkDraft previous = result.removeLast();
                String merged = previous.content() + "\n\n" + join(pending);
                result.add(new ChunkDraft(previous.position(), merged, tokenEstimator.estimate(merged), page,
                        previous.sourceStartOffset(), null));
            } else {
                addChunk(pending, page, result);
            }
        }
    }

    private List<String> semanticBlocks(String content) {
        String normalized = content.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCode = false;
        boolean inList = false;
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (!inCode && current.length() > 0) {
                    flush(current, blocks);
                }
                current.append(line).append('\n');
                inCode = !inCode;
                if (!inCode) {
                    flush(current, blocks);
                }
                inList = false;
                continue;
            }
            if (inCode) {
                current.append(line).append('\n');
                continue;
            }
            boolean listLine = trimmed.matches("^(?:[-*+]\\s+|\\d+[.)]\\s+).*");
            if (trimmed.isEmpty()) {
                flush(current, blocks);
                inList = false;
            } else if (isHeading(trimmed)) {
                flush(current, blocks);
                blocks.add(trimmed);
                inList = false;
            } else if (listLine) {
                if (!inList) {
                    flush(current, blocks);
                }
                current.append(line).append('\n');
                inList = true;
            } else {
                if (inList) {
                    flush(current, blocks);
                    inList = false;
                }
                current.append(line).append('\n');
            }
        }
        flush(current, blocks);
        return blocks;
    }

    private List<String> splitOversizedBlock(String block) {
        int contentLimit = targetTokens - overlapTokens;
        if (tokenEstimator.estimate(block) <= contentLimit) {
            return List.of(block);
        }
        List<String> lines = List.of(block.split("\n"));
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String line : lines) {
            String candidate = piece.isEmpty() ? line : piece + "\n" + line;
            if (!piece.isEmpty() && tokenEstimator.estimate(candidate) > contentLimit) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            if (!piece.isEmpty()) {
                piece.append('\n');
            }
            piece.append(line);
            if (tokenEstimator.estimate(piece.toString()) > contentLimit) {
                splitLongLine(piece.toString(), pieces, contentLimit);
                piece.setLength(0);
            }
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    private void splitLongLine(String line, List<String> pieces, int contentLimit) {
        StringBuilder piece = new StringBuilder();
        for (String word : line.split("\\s+")) {
            String candidate = piece.isEmpty() ? word : piece + " " + word;
            if (!piece.isEmpty() && tokenEstimator.estimate(candidate) > contentLimit) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            if (!piece.isEmpty()) {
                piece.append(' ');
            }
            piece.append(word);
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
    }

    private List<String> overlapTail(List<String> previous, int budget) {
        List<String> overlap = new ArrayList<>();
        int count = 0;
        if (budget <= 0) {
            return overlap;
        }
        for (int index = previous.size() - 1; index >= 0; index--) {
            String block = previous.get(index);
            int tokens = tokenEstimator.estimate(block);
            if (tokens > budget - count) {
                String suffix = semanticSuffix(block, budget - count);
                if (!suffix.isEmpty()) {
                    overlap.add(0, suffix);
                }
                break;
            }
            overlap.add(0, block);
            count += tokens;
            if (count >= budget) {
                break;
            }
        }
        return overlap;
    }

    private String semanticSuffix(String block, int budget) {
        if (budget <= 0 || block.startsWith("```") || block.startsWith("~~~")) {
            return "";
        }
        String[] lines = block.split("\n");
        boolean isList = List.of(lines).stream()
                .allMatch(line -> line.strip().matches("^(?:[-*+]\\s+|\\d+[.)]\\s+).*"));
        if (isList) {
            List<String> suffix = new ArrayList<>();
            for (int index = lines.length - 1; index >= 0; index--) {
                String candidate = lines[index] + (suffix.isEmpty() ? "" : "\n" + String.join("\n", suffix));
                if (tokenEstimator.estimate(candidate) > budget) {
                    break;
                }
                suffix.add(0, lines[index]);
            }
            return String.join("\n", suffix);
        }
        String[] sentences = block.split("(?<=[.!?])\\s+");
        List<String> suffix = new ArrayList<>();
        for (int index = sentences.length - 1; index >= 0; index--) {
            String candidate = sentences[index] + (suffix.isEmpty() ? "" : " " + String.join(" ", suffix));
            if (tokenEstimator.estimate(candidate) > budget) {
                break;
            }
            suffix.add(0, sentences[index]);
        }
        if (!suffix.isEmpty()) {
            return String.join(" ", suffix);
        }
        String[] words = block.strip().split("\\s+");
        suffix.clear();
        for (int index = words.length - 1; index >= 0; index--) {
            String candidate = words[index] + (suffix.isEmpty() ? "" : " " + String.join(" ", suffix));
            if (tokenEstimator.estimate(candidate) > budget) {
                break;
            }
            suffix.add(0, words[index]);
        }
        return String.join(" ", suffix);
    }

    private void addChunk(List<String> blocks, Integer page, List<ChunkDraft> result) {
        String content = join(blocks);
        result.add(new ChunkDraft(result.size(), content, tokenEstimator.estimate(content), page, null, null));
    }

    private static String join(List<String> blocks, String next) {
        List<String> joined = new ArrayList<>(blocks);
        joined.add(next);
        return join(joined);
    }

    private static String join(List<String> blocks) {
        return String.join("\n\n", blocks).strip();
    }

    private static boolean isHeading(String line) {
        return line.matches("^#{1,6}\\s+.*") || line.matches("^.+\\n=+$") || line.matches("^.+\\n-+$");
    }

    private static void flush(StringBuilder current, List<String> blocks) {
        String block = current.toString().strip();
        if (!block.isEmpty()) {
            blocks.add(block);
        }
        current.setLength(0);
    }
}

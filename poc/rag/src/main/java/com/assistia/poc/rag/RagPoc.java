package com.assistia.poc.rag;

import java.util.ArrayList;
import java.util.List;

public final class RagPoc {
    private RagPoc() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: RagPoc <sql|embedding|search>");
        }
        switch (args[0]) {
            case "sql" -> new SqlServerVectorStore().runSqlSmokeTest();
            case "embedding" -> verifyEmbedding();
            case "search" -> runSemanticSearch();
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    private static void verifyEmbedding() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        try (GeminiEmbedder embedder = new GeminiEmbedder(apiKey)) {
            float[] vector = embedder.embedQuery("Pourquoi mon poste ne résout-il plus les noms DNS ?");
            float minimum = Float.POSITIVE_INFINITY;
            float maximum = Float.NEGATIVE_INFINITY;
            for (float value : vector) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
            System.out.printf("Gemini model: %s%n", GeminiEmbedder.MODEL);
            System.out.printf("Embedding dimensions: %d%n", vector.length);
            System.out.printf("All values are finite: yes (range %.6f to %.6f)%n", minimum, maximum);
            System.out.println("Gemini embedding smoke test: PASS");
        }
    }

    private static void runSemanticSearch() throws Exception {
        List<DemoDocument> documents = DemoCorpus.documents();
        List<float[]> documentVectors = new ArrayList<>(documents.size());
        try (GeminiEmbedder embedder = new GeminiEmbedder(System.getenv("GEMINI_API_KEY"))) {
            System.out.printf("Creating embeddings with %s (%d dimensions).%n",
                    GeminiEmbedder.MODEL, GeminiEmbedder.DIMENSIONS);
            for (DemoDocument document : documents) {
                documentVectors.add(embedder.embedDocument(document.title(), document.content()));
                System.out.println("Embedded: " + document.title());
            }

            SqlServerVectorStore store = new SqlServerVectorStore();
            store.initialize();
            store.replaceDocuments(documents, documentVectors);

            List<DemoQuestion> questions = List.of(
                    new DemoQuestion("Mon ordinateur ne parvient plus à résoudre les noms de domaine.", 1),
                    new DemoQuestion("Je n'obtiens plus d'adresse IP automatiquement.", 2),
                    new DemoQuestion("Le client VPN refuse de se connecter.", 3),
                    new DemoQuestion("Mon imprimante réseau n'est plus accessible.", 6),
                    new DemoQuestion("Comment vérifier qu'un service Linux fonctionne ?", 5));

            int expectedTopResults = 0;
            for (DemoQuestion question : questions) {
                float[] queryVector = embedder.embedQuery(question.text());
                List<SqlServerVectorStore.SearchResult> results = store.search(queryVector, documents.size());
                int expectedRank = -1;
                for (int i = 0; i < results.size(); i++) {
                    if (results.get(i).id() == question.expectedDocumentId()) {
                        expectedRank = i + 1;
                        break;
                    }
                }
                System.out.printf("%nQuestion: %s%n", question.text());
                System.out.printf("Expected document rank: %d/%d%n", expectedRank, documents.size());
                boolean expectedIsTopResult = !results.isEmpty()
                        && results.getFirst().id() == question.expectedDocumentId();
                if (expectedIsTopResult) {
                    expectedTopResults++;
                }
                System.out.printf("Top-result check: %s%n", expectedIsTopResult ? "PASS" : "NOT MATCHED");
                for (int i = 0; i < results.size(); i++) {
                    SqlServerVectorStore.SearchResult result = results.get(i);
                    System.out.printf("  %d. [id=%d] %s — cosine distance: %.6f%s%n",
                            i + 1, result.id(), result.title(), result.cosineDistance(),
                            i == 0 ? " (top result)" : "");
                }
            }
            System.out.printf("%nExpected documents ranked first: %d/%d%n",
                    expectedTopResults, questions.size());
        }
    }

    private record DemoQuestion(String text, int expectedDocumentId) {
    }
}

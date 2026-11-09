package com.assistia.repository;

import microsoft.sql.Vector;
import microsoft.sql.Vector.VectorDimensionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class KnowledgeChunkVectorRepository {
    private static final String FIND_CHUNKS = """
            SELECT id, chunk_position, content, embedding, embedding_model, embedding_dimension
            FROM dbo.knowledge_chunks
            WHERE document_id = ? AND document_version = ?
            ORDER BY chunk_position
            """;

    private static final String UPDATE_EMBEDDING = """
            UPDATE dbo.knowledge_chunks
            SET embedding = ?, embedding_model = ?, embedding_dimension = ?, updated_at = SYSUTCDATETIME()
            WHERE id = ? AND document_id = ? AND document_version = ?
              AND embedding IS NULL AND embedding_model IS NULL AND embedding_dimension IS NULL
            """;

    private final JdbcTemplate jdbc;

    public KnowledgeChunkVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChunkVector> findByDocumentAndVersion(Long documentId, Integer version) {
        return jdbc.query(FIND_CHUNKS, (result, rowNumber) -> map(result), documentId, version);
    }

    public boolean saveEmbedding(
            Long chunkId, Long documentId, Integer version, float[] values, String model, int dimensions) {
        Vector vector = toSqlVector(values, dimensions);
        int updated = jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(UPDATE_EMBEDDING);
            statement.setObject(1, vector, microsoft.sql.Types.VECTOR);
            statement.setNString(2, model);
            statement.setInt(3, dimensions);
            statement.setLong(4, chunkId);
            statement.setLong(5, documentId);
            statement.setInt(6, version);
            return statement;
        });
        return updated == 1;
    }

    private static ChunkVector map(ResultSet result) throws SQLException {
        Vector vector = result.getObject("embedding", Vector.class);
        if (result.wasNull()) {
            vector = null;
        }
        return new ChunkVector(
                result.getLong("id"),
                result.getInt("chunk_position"),
                result.getNString("content"),
                vector,
                result.getNString("embedding_model"),
                (Integer) result.getObject("embedding_dimension"));
    }

    private static Vector toSqlVector(float[] values, int dimensions) {
        if (values == null || values.length != dimensions || dimensions != 1536) {
            throw new IllegalArgumentException("Embedding has an unexpected dimension.");
        }
        Float[] boxed = new Float[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) {
                throw new IllegalArgumentException("Embedding contains a non-finite value.");
            }
            boxed[i] = values[i];
        }
        return new Vector(values.length, VectorDimensionType.FLOAT32, boxed);
    }

    public record ChunkVector(
            long id,
            int position,
            String content,
            Vector vector,
            String model,
            Integer dimension) {
    }
}

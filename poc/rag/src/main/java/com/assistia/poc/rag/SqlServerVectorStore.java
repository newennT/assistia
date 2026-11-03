package com.assistia.poc.rag;

import microsoft.sql.Vector;
import microsoft.sql.Vector.VectorDimensionType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqlServerVectorStore {
    private static final String DATABASE = "AssistIARagPoc";
    private static final String TABLE = "dbo.rag_poc_documents";
    private static final String MASTER_URL = System.getenv().getOrDefault(
            "RAG_POC_JDBC_URL",
            "jdbc:sqlserver://localhost:11434;databaseName=master;encrypt=true;"
                    + "trustServerCertificate=true;loginTimeout=30;vectorTypeSupport=v2");

    private final String username;
    private final String password;

    public SqlServerVectorStore() {
        username = System.getenv().getOrDefault("RAG_POC_SQLSERVER_USER", "sa");
        password = System.getenv("RAG_POC_SQLSERVER_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("RAG_POC_SQLSERVER_PASSWORD must be set.");
        }
    }

    public void initialize() throws SQLException {
        try (Connection master = DriverManager.getConnection(MASTER_URL, username, password)) {
            printVersions(master);
            try (Statement statement = master.createStatement()) {
                statement.execute("""
                        IF DB_ID(N'AssistIARagPoc') IS NULL
                            CREATE DATABASE AssistIARagPoc
                        """);
            }
        }
        try (Connection connection = connectDatabase();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            statement.execute("""
                    CREATE TABLE dbo.rag_poc_documents (
                        id INT NOT NULL PRIMARY KEY,
                        title NVARCHAR(200) NOT NULL,
                        content NVARCHAR(MAX) NOT NULL,
                        embedding VECTOR(1536) NOT NULL
                    )
                    """);
        }
    }

    public void runSqlSmokeTest() throws SQLException {
        initialize();
        float[] vector = new float[GeminiEmbedder.DIMENSIONS];
        vector[0] = 1.0f;
        insert(new DemoDocument(999, "SQL VECTOR smoke test", "Synthetic basis vector."), vector);

        try (Connection connection = connectDatabase();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT id, title, embedding FROM " + TABLE + " WHERE id = 999")) {
            if (!result.next()) {
                throw new SQLException("Inserted VECTOR row could not be read.");
            }
            Vector sqlVector = result.getObject("embedding", Vector.class);
            if (sqlVector == null) {
                throw new SQLException("JDBC returned a null VECTOR value.");
            }
            validateReadVector(sqlVector);
            System.out.printf("SQL readback: id=%d, title=%s, dimensions=%d, first values=%s%n",
                    result.getInt("id"), result.getString("title"),
                    sqlVector.getDimensionCount(), firstValues(sqlVector));
        }

        try (Connection connection = connectDatabase();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT VECTOR_DISTANCE('cosine', ?, embedding) AS cosine_distance
                        FROM dbo.rag_poc_documents
                        WHERE id = 999
                        """)) {
            query.setObject(1, toSqlVector(vector), microsoft.sql.Types.VECTOR);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("VECTOR_DISTANCE returned no row.");
                }
                double distance = result.getDouble("cosine_distance");
                if (!Double.isFinite(distance) || Math.abs(distance) > 0.0001) {
                    throw new SQLException("Expected near-zero cosine distance, got " + distance + ".");
                }
                System.out.printf("SQL VECTOR_DISTANCE('cosine', identical vector): %.8f%n", distance);
            }
        }
        System.out.println("SQL Server VECTOR smoke test: PASS");
    }

    public void replaceDocuments(List<DemoDocument> documents, List<float[]> vectors) throws SQLException {
        if (documents.size() != vectors.size()) {
            throw new IllegalArgumentException("Documents and vectors must have matching counts.");
        }
        try (Connection connection = connectDatabase();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + TABLE);
        }
        for (int i = 0; i < documents.size(); i++) {
            insert(documents.get(i), vectors.get(i));
        }
        System.out.printf("Indexed %d demo documents in %s.%n", documents.size(), DATABASE);
    }

    public List<SearchResult> search(float[] queryVector, int limit) throws SQLException {
        String sql = """
                SELECT TOP (?) id, title,
                       VECTOR_DISTANCE('cosine', ?, embedding) AS cosine_distance
                FROM dbo.rag_poc_documents
                ORDER BY cosine_distance ASC
                """;
        try (Connection connection = connectDatabase();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setObject(2, toSqlVector(queryVector), microsoft.sql.Types.VECTOR);
            try (ResultSet result = statement.executeQuery()) {
                List<SearchResult> results = new ArrayList<>();
                while (result.next()) {
                    results.add(new SearchResult(
                            result.getInt("id"),
                            result.getString("title"),
                            result.getDouble("cosine_distance")));
                }
                return results;
            }
        }
    }

    private void insert(DemoDocument document, float[] values) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (id, title, content, embedding) VALUES (?, ?, ?, ?)";
        try (Connection connection = connectDatabase();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, document.id());
            statement.setNString(2, document.title());
            statement.setNString(3, document.content());
            statement.setObject(4, toSqlVector(values), microsoft.sql.Types.VECTOR);
            statement.executeUpdate();
        }
    }

    private Connection connectDatabase() throws SQLException {
        String databaseUrl = MASTER_URL.replace("databaseName=master", "databaseName=" + DATABASE);
        return DriverManager.getConnection(databaseUrl, username, password);
    }

    private static void printVersions(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)) AS version")) {
            if (!result.next()) {
                throw new SQLException("Cannot determine SQL Server version.");
            }
            String serverVersion = result.getString("version");
            System.out.printf("SQL Server: %s (%s)%n", serverVersion, metadata.getDatabaseProductName());
            System.out.printf("JDBC driver: %s%n", metadata.getDriverVersion());
            if (!serverVersion.startsWith("17.")) {
                throw new SQLException("Expected SQL Server 2025 (major version 17), got " + serverVersion + ".");
            }
        }
    }

    private static Vector toSqlVector(float[] values) {
        if (values.length != GeminiEmbedder.DIMENSIONS) {
            throw new IllegalArgumentException("Expected " + GeminiEmbedder.DIMENSIONS
                    + " vector dimensions, got " + values.length + ".");
        }
        Float[] boxed = new Float[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) {
                throw new IllegalArgumentException("Vector contains a non-finite value at index " + i + ".");
            }
            boxed[i] = values[i];
        }
        return new Vector(values.length, VectorDimensionType.FLOAT32, boxed);
    }

    private static void validateReadVector(Vector vector) throws SQLException {
        if (vector.getDimensionCount() != GeminiEmbedder.DIMENSIONS
                || vector.getVectorDimensionType() != VectorDimensionType.FLOAT32
                || vector.getData() == null
                || vector.getData().length != GeminiEmbedder.DIMENSIONS) {
            throw new SQLException("Read-back VECTOR has an unexpected type or dimension.");
        }
        for (Object value : vector.getData()) {
            if (!(value instanceof Float number) || !Float.isFinite(number)) {
                throw new SQLException("Read-back VECTOR contains a non-finite or non-numeric value.");
            }
        }
    }

    private static String firstValues(Vector vector) {
        Object[] values = vector.getData();
        return "[" + values[0] + ", " + values[1] + ", " + values[2] + "]";
    }

    public record SearchResult(int id, String title, double cosineDistance) {
    }
}

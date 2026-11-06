# AssistIA RAG compatibility proof of concept

This standalone Java 21 project tests SQL Server 2025 `VECTOR(1536)`, the Microsoft JDBC vector mapping, Gemini Embeddings 2, and cosine-distance semantic search. It does not import or modify the AssistIA backend, its migrations, its main Compose file, or its SQL Server 2022 volume.

## Versions

- Java: 21
- Gemini Java SDK: `com.google.genai:google-genai:1.79.0` (same as the backend)
- Microsoft SQL Server JDBC: `com.microsoft.sqlserver:mssql-jdbc:13.6.0.jre11`
- SQL Server: `mcr.microsoft.com/mssql/server:2025-latest` (SQL Server 2025, major version 17)
- Gemini embedding model: `gemini-embedding-2`, requested output size 1,536

The backend's Spring Boot dependency management currently resolves the JDBC driver to `12.10.1.jre11`. The POC pins the newer 13.6.0 driver: Microsoft lists Java 21 as supported and the JDBC type mapping `VECTOR` ↔ `microsoft.sql.Vector`. The vector Java class is present since 13.3.2. The POC tests native binding and retrieval with the newer driver rather than assuming that the currently resolved backend version supports it.

## Start the isolated SQL Server 2025

From the repository root, generate a unique local-only SQL password in an ignored file and start the dedicated service:

```sh
umask 077
python3 -c "from pathlib import Path; import secrets; Path('poc/rag/.env').write_text('RAG_POC_SQLSERVER_PASSWORD=' + secrets.token_urlsafe(32) + '\n')"
docker compose --env-file poc/rag/.env -p assistia-rag-poc -f poc/rag/compose.yaml up -d --wait
```

The test instance is bound only to `127.0.0.1:11434`, uses its own Compose-managed volume (`assistia-rag-poc_rag_poc_sqlserver_data`), and creates a separate `AssistIARagPoc` database. The existing `assistia-sqlserver` container and `sqlserver_data` volume are not referenced by this Compose file.

To confirm which isolated containers are running:

```sh
docker ps --filter name=assistia-rag-poc-sqlserver-2025
```

## Run the SQL Server check

From the repository root, load the local SQL password and run the test:

```sh
set -a
. ./poc/rag/.env
set +a
cd poc/rag
mvn -q compile exec:java -Dexec.args=sql
```

This reports the SQL Server and JDBC driver versions, creates the isolated database and a `VECTOR(1536)` table, writes and reads back a typed `microsoft.sql.Vector`, then checks that cosine distance from an identical vector is approximately zero. Running this command again drops and recreates only `dbo.rag_poc_documents` in the POC database.

JDBC defaults to `localhost:11434`, user `sa`, and negotiates `vectorTypeSupport=v2`. The driver accepts `off`, `v1`, or `v2`; `on` is not a valid value. For a different local port or login, set `RAG_POC_JDBC_URL`, `RAG_POC_SQLSERVER_USER`, and `RAG_POC_SQLSERVER_PASSWORD`. The default connection enables TLS and trusts the self-signed certificate used by the local development container; do not copy this trust setting into a production connection.

## Run the Gemini embedding check

Make the existing local key available to the Java process without adding it to this project or committing it. For example, from the repository root in a Bash shell:

```sh
set -a
. ./.env
set +a
cd poc/rag
mvn -q compile exec:java -Dexec.args=embedding
```

Alternatively, export `GEMINI_API_KEY` through your normal secret manager. The program fails clearly if it is missing. The key is passed directly to the Java SDK's `Client` and is never printed. Do not send real tickets or internal documents; the built-in French sample text is synthetic.

The check requests one embedding, verifies exactly 1,536 finite numeric values, and prints only the model, dimensions, and numeric range.

## Run semantic search

With the same environment variables available, run:

```sh
set -a
. ./.env
. ./poc/rag/.env
set +a
cd poc/rag
mvn -q compile exec:java -Dexec.args=search
```

This generates one embedding for each of the six fictitious documents and each of the five French questions, stores the document vectors in the POC database, and prints every result ordered by `VECTOR_DISTANCE('cosine', ...)`. It reports the expected document's rank and every raw distance; there is deliberately no universal relevance threshold.

Gemini Embedding 2's documented text retrieval format is used: documents use `title: ... | text: ...`, and questions use `task: search result | query: ...`. Each text causes an API request in this simple demo, so the complete search makes eleven embedding requests.

## Remove only the POC

After stopping any running POC command, remove only the isolated Compose project and its volume:

```sh
docker compose --env-file poc/rag/.env -p assistia-rag-poc -f poc/rag/compose.yaml down -v
```

This removes the POC container and its own named volume. It does not stop or delete the AssistIA containers or their SQL Server volume. Delete the ignored local password file (`poc/rag/.env`) separately after shutdown. The Java source and its Maven `target/` directory can be removed separately if no longer needed.

## Interpreting the results

An embedding is a fixed-length numeric representation of text. Cosine distance measures the angle between two vectors; smaller values indicate greater directional similarity. It is not a probability or a universal confidence score. Review the expected document's rank and distances across the questions before selecting a retrieval threshold.

The search is exact: `VECTOR_DISTANCE` calculates distances across all six rows. This validates type support and the end-to-end data path, not production-scale performance or an approximate vector index.

## AssistIA backend document model (Phase 2)

The backend Flyway V5 migration adds the relational document lifecycle and chunk storage. It preserves the original `content`, `category`, and `source_reference` columns. Existing rows receive `current_version = 1`, `indexing_version = 1`, `indexing_status = 'PENDING'`, and `imported_at = created_at`; filename, media type, byte size, checksum, and importer remain unknown/null.

In `knowledge_documents`, `original_filename`, `mime_type`, `file_size_bytes`, and `content_sha256` describe the source file; `imported_at` and nullable `imported_by_id` record import provenance. `created_at`/`updated_at` remain the existing row audit dates. `current_version` is the published version; `indexing_version` plus `indexing_status` describe the candidate build.

`knowledge_documents.current_version` identifies the version currently published. `indexing_version` identifies the candidate targeted by `indexing_status`. This lets the system prepare chunks for version N+1 while version N remains current. Only after all chunks and embeddings for the target are ready should a future embedding pipeline atomically set `current_version = indexing_version` and `indexing_status = 'INDEXED'`. The V6 migration adds `AWAITING_EMBEDDINGS`; Phase 3 ends in that state, without changing `current_version` or generating embeddings. A failed attempt can set `FAILED` without removing the prior version. `OUTDATED` indicates re-indexing is needed.

`knowledge_chunks` stores versioned text fragments with a unique document/version/position tuple, optional source page and offsets, and a nullable SQL Server `VECTOR(1536)`. It can hold multiple versions concurrently. There is intentionally no approximate vector index. The vector column is not mapped by Hibernate; future vector reads and writes use JDBC with `microsoft.sql.Vector`, `setObject(..., Types.VECTOR)`, and `getObject(..., Vector.class)`.

Each chunk row holds `document_id`, `document_version`, zero-based `chunk_position`, `content`, and `estimated_token_count`; `source_page`, `source_start_offset`, and `source_end_offset` locate text in its source when known. `embedding_model` and `embedding_dimension` describe a populated vector; all embedding data can remain null before indexing. `created_at` and `updated_at` audit the chunk row. The SQL vector type enforces 1,536 dimensions, and the relational dimension check rejects any non-null `embedding_dimension` other than 1,536.

Chunks are not cascaded from document deletion, and the schema does not cascade-delete old versions. This preserves citation targets. Proposed later `ai_analysis_sources` columns are `id`, `ai_analysis_id`, `document_id`, `document_version`, `chunk_id`, nullable `citation_rank`, nullable `distance`, and `created_at`. It should have a foreign key from `ai_analysis_id` to `ai_analyses(id)` and a composite foreign key `(chunk_id, document_id, document_version)` to the corresponding chunk. V6 will need a unique key on `knowledge_chunks(id, document_id, document_version)` to support that composite foreign key. Add a uniqueness rule for `(ai_analysis_id, chunk_id)` if one analysis can cite a chunk only once, and check non-null ranks are positive and distances nonnegative. Do not cascade-delete sources or chunks while analyses are retained. The table is deferred until analysis deletion/retention behavior and citation cardinality are defined. Distance is meaningful only alongside the retrieval metric/model context, which can be added if query auditability requires it.

The relational columns use checks for legal lifecycle states, nonnegative current version (zero means no published version yet), positive candidate/chunk versions, nonnegative sizes/counts, ordered source offsets, checksum format, and the fixed 1,536 embedding dimension. The importing user is nullable and references `users`; V5 does not attribute legacy documents to the system user.

## AssistIA document ingestion (Phase 3)

The backend accepts only `.md`, `.markdown`, and `.pdf` uploads. Markdown is decoded strictly as UTF-8; headings, paragraphs, lists, and fenced code are kept as semantic blocks. PDFBox extracts each page separately and records its 1-based page number on each chunk. A PDF must have a PDF signature and parse successfully; files with no usable text are rejected. There is no OCR. The client MIME type is ignored.

Imports require `TECHNICIAN` or `ADMIN`. The role check runs in Spring Security for every knowledge-document endpoint. The existing cookie/session CSRF protection applies to the multipart `POST` import and archive `POST`; the frontend must send `X-XSRF-TOKEN` exactly as for other state-changing endpoints. Files are limited to 20 MiB by default at both the multipart parser and service layer (`ASSISTIA_KNOWLEDGE_MAX_FILE_SIZE`, `ASSISTIA_KNOWLEDGE_MAX_FILE_SIZE_BYTES`; request size is configurable with `ASSISTIA_KNOWLEDGE_MAX_REQUEST_SIZE`).

Original files are written outside the public web root, by default to `${user.home}/.assistia/knowledge-documents`; override with `ASSISTIA_KNOWLEDGE_STORAGE_DIRECTORY`. Stored keys are generated by the backend, filenames are reduced to a sanitized basename, storage writes use a temporary file and atomic move when supported, and POSIX permissions are restricted when available. `knowledge_document_versions` keeps each version's checksum and storage key; SHA-256 uniqueness rejects exact duplicates even if names differ or a later version has since been imported. No original file is stored in SQL Server and imported content is never executed or sent to Gemini.

The ingestion sequence commits `PROCESSING` and source metadata, writes the original file, extracts/chunks outside a SQL transaction, then atomically persists the complete chunk set and `AWAITING_EMBEDDINGS`. A failed extraction or persistence attempt records `FAILED`; candidate chunks are inserted in a single transaction, so an incomplete candidate is never visible. `current_version` stays unchanged until a future embedding pipeline publishes the candidate. A retry of the same failed candidate reuses its version; otherwise the next version is chosen above both the published and attempted versions. Versions and chunks are never physically deleted; archive is logical (`archived_at`).

Chunk sizing is configurable with `ASSISTIA_KNOWLEDGE_CHUNK_TARGET_TOKENS` (700) and `ASSISTIA_KNOWLEDGE_CHUNK_OVERLAP_TOKENS` (100). The minimum size is 40 estimated tokens, except when the whole source or an indivisible semantic block is shorter. The implementation uses a replaceable `TokenEstimator`, currently a documented coarse estimate of one token per four Unicode code points; it is not a tokenizer and may vary significantly for code, identifiers, and multilingual text. Chunking prefers paragraph, heading, list, and fenced-code boundaries; very long blocks fall back to line/word boundaries. PDF page boundaries are retained.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/knowledge-documents` | `TECHNICIAN`/`ADMIN` + CSRF | Multipart import (`file`, optional `documentId`, `title`, `category`); `documentId` starts a new version |
| `GET` | `/api/knowledge-documents` | `TECHNICIAN`/`ADMIN` | List non-archived document metadata |
| `GET` | `/api/knowledge-documents/{id}` | `TECHNICIAN`/`ADMIN` | Document metadata and lifecycle state |
| `GET` | `/api/knowledge-documents/{id}/versions/{version}/chunks` | `TECHNICIAN`/`ADMIN` | Read chunks for the published or latest ready candidate version |
| `POST` | `/api/knowledge-documents/{id}/archive` | `TECHNICIAN`/`ADMIN` + CSRF | Archive without physically deleting chunks or originals |

The phase does not implement embeddings, physical cleanup/retention of stored originals, OCR, token-exact counts, version publication, vector access, similarity search, or RAG. Failed and superseded original files are retained privately; an explicit retention policy is still needed before automated cleanup.

To run the SQL Server integration tests, start the isolated SQL Server 2025 candidate service and create a disposable database (never use the main AssistIA database):

```sh
set -a
. ./.env.sqlserver2025
set +a
docker compose -f docker-compose.sqlserver2025.yml up -d --wait
docker exec -it assistia-sqlserver-2025-candidate \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C \
  -Q "IF DB_ID('AssistIAV6Test') IS NULL CREATE DATABASE AssistIAV6Test"
cd backend
ASSISTIA_SQLSERVER_V6_TESTS=true \
ASSISTIA_SQLSERVER_V6_TEST_PASSWORD="$SPRING_DATASOURCE_PASSWORD" \
mvn -q -Dtest=KnowledgeDocumentV6SqlServerTest test
```

`sqlcmd` prompts for the isolated server password. The test profile defaults to the candidate server at `localhost:1434` and uses `vectorTypeSupport=v2`.

For a manual import, start the backend in one terminal against the isolated SQL Server 2025 instance and disposable database (for example, `AssistIAV6Test`):

```sh
set -a
. ./.env.sqlserver2025
set +a
cd backend
SPRING_PROFILES_ACTIVE=sqlserver2025 \
SPRING_DATASOURCE_URL='jdbc:sqlserver://localhost:1434;databaseName=AssistIAV6Test;encrypt=true;trustServerCertificate=true;vectorTypeSupport=v2' \
SPRING_DATASOURCE_USERNAME=sa \
mvn spring-boot:run
```

In another terminal, with `curl` and `jq`, `umask 077`, log in as a provisioned technician/admin and refresh the CSRF token after authentication:

```sh
umask 077
CSRF_TOKEN=$(curl -sS -c cookies.txt -b cookies.txt http://localhost:8080/api/auth/csrf | jq -r .token)
read -r -s -p 'Mot de passe: ' ASSISTIA_PASSWORD
printf '\n'
LOGIN_BODY=$(jq -nc --arg email 'technician@example.test' --arg password "$ASSISTIA_PASSWORD" \
  '{email:$email,password:$password}')
curl -sS -c cookies.txt -b cookies.txt -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" --data "$LOGIN_BODY" \
  http://localhost:8080/api/auth/login
unset ASSISTIA_PASSWORD LOGIN_BODY
CSRF_TOKEN=$(curl -sS -c cookies.txt -b cookies.txt http://localhost:8080/api/auth/csrf | jq -r .token)
curl -i -c cookies.txt -b cookies.txt -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -F 'file=@./guide.md' -F 'category=GENERAL' \
  http://localhost:8080/api/knowledge-documents
rm -f cookies.txt
```

Login rotates the CSRF token; the command retrieves the current token after login. Replace the sample account with a provisioned account. A successful response is `201 Created` with the document ID, version, checksum, and `AWAITING_EMBEDDINGS`. Listing the document and fetching its chunks use the `GET` endpoints above.

## Results of the compatibility run

Run on 2026-11-03:

| Component | Observed version/result |
|---|---|
| Java runtime | OpenJDK 21.0.12.1 |
| AssistIA's existing SQL Server | Image `2022-latest`, server `16.0.4295.3`; left running and unchanged |
| Isolated POC SQL Server | Image `2025-latest`, server `17.0.5035.5` |
| AssistIA backend JDBC dependency | `12.10.1.jre11` (Spring Boot-managed) |
| POC JDBC dependency | `13.6.0.jre11`; driver reports `13.6.0.0` |
| Gemini Java SDK | `1.79.0` in both projects |
| `VECTOR(1536)` write/read and cosine smoke test | PASS; identical-vector distance `0.00000000` |
| Gemini Embedding 2 dimensions and finite-value check | PASS; 1,536 dimensions, observed range `-0.191717` to `0.184951` |

The semantic run indexed all six fictional documents. Each of the five questions ranked its expected subject first:

| Question topic | Top document | Cosine distance |
|---|---|---:|
| DNS name resolution | DNS — résolution et diagnostic | 0.245752 |
| Automatic IP assignment | DHCP — attribution d'adresses IP | 0.222920 |
| VPN client connection | VPN — diagnostic de connexion | 0.167995 |
| Network printer unavailable | Imprimantes réseau — connectivité IP | 0.178389 |
| Linux service status | Linux — vérification des services réseau | 0.227199 |

The next results are not all irrelevant: Windows network diagnostics shares terminology with DNS, DHCP, and printer issues, and Linux DNS configuration is close to DNS questions. This tiny hand-written corpus demonstrates basic retrieval separation, but it does not establish a production relevance threshold or general accuracy.

Two useful compatibility findings came from the SQL test:

- `vectorTypeSupport=on` is rejected by JDBC 13.6. Valid settings are `off`, `v1`, and `v2`; the POC uses `v2`.
- Untyped `ResultSet.getObject("embedding")` returned a `byte[]` on this connection. The JDBC typed getter `getObject("embedding", microsoft.sql.Vector.class)` correctly returned the 1,536-float `Vector`. The POC therefore uses the typed getter explicitly.

The official pricing table, checked on the run date, lists Gemini Embedding 2 text input at **$0.20 per million input tokens** on Standard paid usage and free of charge on its Free tier; the table marks Free-tier content as used to improve products and paid-tier content as not used for that purpose. The semantic search executed eleven embedding requests over synthetic texts (six document inputs and five queries), in addition to the one request in the standalone embedding check. The API response/client did not report token usage in the CLI output, so no exact per-run dollar total is claimed. Gemini model limits are project/tier-specific and should be checked in AI Studio. The model accepts up to 8,192 input tokens. Check Google's current [pricing](https://ai.google.dev/gemini-api/docs/pricing), [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits), and [terms](https://ai.google.dev/gemini-api/docs/terms) before deployment. Google's terms also state special paid-service data terms apply to users in the EEA, Switzerland, and UK, including use of unpaid quota.

Gemini quota and billing depend on the API project's tier, active model limits, and input token volume. Free-tier usage has different data-use terms than the paid tier; check Google's current [pricing](https://ai.google.dev/gemini-api/docs/pricing), [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits), and [terms](https://ai.google.dev/gemini-api/docs/terms) before sending operational data. This POC uses only its fictional samples.

package com.assistia.service.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Component
public class KnowledgeDocumentStorage {
    private final Path root;

    public KnowledgeDocumentStorage(
            @Value("${assistia.knowledge.storage-directory:${user.home}/.assistia/knowledge-documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        rejectKnownPublicDirectories(this.root);
    }

    public void store(String storageKey, byte[] bytes) throws IOException {
        Path destination = resolve(storageKey);
        Files.createDirectories(destination.getParent());
        setPermissions(destination.getParent(), Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));

        Path temporary = Files.createTempFile(destination.getParent(), ".import-", ".tmp");
        try {
            setPermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private Path resolve(String storageKey) throws IOException {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Clé de stockage invalide.");
        }
        Files.createDirectories(root);
        setPermissions(root, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        Path realRoot = root.toRealPath();
        try {
            rejectKnownPublicDirectories(realRoot);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Le répertoire de stockage documentaire doit rester privé.");
        }
        Path parent = resolved.getParent();
        Files.createDirectories(parent);
        setPermissions(parent, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        if (!parent.toRealPath().startsWith(realRoot)) {
            throw new IOException("Répertoire de stockage invalide.");
        }
        return resolved;
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are not available on every supported filesystem.
        }
    }

    private static void rejectKnownPublicDirectories(Path root) {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String relative : Set.of(
                "frontend/public",
                "frontend/dist",
                "backend/src/main/resources/static",
                "backend/src/main/resources/public")) {
            if (root.startsWith(workingDirectory.resolve(relative).normalize())) {
                throw new IllegalArgumentException("Le répertoire de stockage documentaire doit rester privé.");
            }
        }
    }
}

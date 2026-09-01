package com.booki.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Default storage: files live under one local directory
 * ({@code booki.storage.local-path}, default {@code ./storage}), with the key
 * used as the relative path — e.g. {@code documents/x.pdf} →
 * {@code ./storage/documents/x.pdf}. Fine for local dev and single-instance
 * runs; not shared between instances and wiped on an ephemeral redeploy, which
 * is why the {@code s3} driver exists (see {@link S3StorageAdapter}).
 */
@Component
@ConditionalOnProperty(name = "booki.storage.driver", havingValue = "local", matchIfMissing = true)
public class LocalStorageAdapter implements StorageAdapter {

    private final Path root;

    public LocalStorageAdapter(@Value("${booki.storage.local-path:./storage}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Failed to write " + key, e);
        }
    }

    @Override
    public Resource get(String key) {
        Resource resource = new FileSystemResource(resolve(key));
        if (!resource.exists()) {
            throw new StorageException("No stored file for key " + key);
        }
        return resource;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    @Override
    public void ping() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Storage directory unavailable: " + root, e);
        }
        if (!Files.isWritable(root)) {
            throw new StorageException("Storage directory not writable: " + root);
        }
    }

    /** Resolve a key under {@link #root}, refusing anything that escapes it. */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        return resolved;
    }
}

package com.booki.storage;

import org.springframework.core.io.Resource;

/**
 * Blob storage for the two kinds of files BooKI keeps outside the database:
 * uploaded PDFs and generated report/summary PDFs.
 *
 * <p>Everything is addressed by an opaque <em>key</em> — a forward-slash path
 * such as {@code documents/<uuid>_<name>.pdf} or {@code reports/<uuid>.pdf}. The
 * key is what gets persisted (in {@code documents.file_path} /
 * {@code sent_reports.file_name}), never an absolute filesystem path, so the
 * same rows work whichever implementation is active.
 *
 * <p>Implementations are selected by {@code booki.storage.driver}
 * ({@code local} — the default — or {@code s3}).
 */
public interface StorageAdapter {

    /** Store {@code content} at {@code key}, overwriting any existing object. */
    void put(String key, byte[] content, String contentType);

    /** A readable {@link Resource} for {@code key}. Throws {@link StorageException} if it is missing. */
    Resource get(String key);

    /** Delete {@code key}. A no-op if it does not exist. */
    void delete(String key);

    /**
     * Cheap reachability check for the backing store — throws
     * {@link StorageException} if it is unavailable. Writes nothing. Used by the
     * {@code storage} entry under {@code /actuator/health}.
     */
    void ping();
}

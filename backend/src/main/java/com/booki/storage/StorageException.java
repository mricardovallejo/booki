package com.booki.storage;

/**
 * A storage operation (read/write/delete) failed. Maps to HTTP 500 via
 * {@code GlobalExceptionHandler}'s catch-all for {@link RuntimeException}.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}

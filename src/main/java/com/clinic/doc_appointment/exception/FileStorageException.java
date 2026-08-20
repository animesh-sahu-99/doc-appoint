package com.clinic.doc_appointment.exception;

/**
 * A stored file could not be written, read or removed.
 *
 * <p>Exists so the storage layer stops throwing bare {@link RuntimeException}, which landed on the
 * catch-all handler and turned every storage problem — including a simple missing file — into an
 * opaque 500. A genuinely absent file throws {@link ResourceNotFoundException} instead.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

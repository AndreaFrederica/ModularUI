package com.cleanroommc.modularui.api.sync.document;

import java.util.Objects;

/** Expected endpoint or protocol failure whose message is safe to expose to the remote peer. */
public class DocumentSyncException extends Exception {

    private final DocumentSyncErrorCode code;

    public DocumentSyncException(DocumentSyncErrorCode code, String safeMessage) {
        super(normalizeMessage(safeMessage));
        this.code = Objects.requireNonNull(code, "code");
    }

    public DocumentSyncException(DocumentSyncErrorCode code, String safeMessage, Throwable cause) {
        super(normalizeMessage(safeMessage), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DocumentSyncErrorCode getCode() {
        return this.code;
    }

    private static String normalizeMessage(String message) {
        String normalized = Objects.requireNonNull(message, "safeMessage").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Safe error message must not be empty");
        return normalized;
    }
}

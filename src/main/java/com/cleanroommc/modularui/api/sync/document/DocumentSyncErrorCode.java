package com.cleanroommc.modularui.api.sync.document;

/** Stable, non-sensitive error codes used by document synchronization. */
public enum DocumentSyncErrorCode {
    PROTOCOL_MISMATCH,
    NOT_READY,
    INVALID_REQUEST,
    ENDPOINT_NOT_FOUND,
    SCHEMA_MISMATCH,
    FORBIDDEN,
    INVALID_PARAMS,
    CHANNEL_NOT_FOUND,
    REVISION_MISMATCH,
    COMMAND_NOT_FOUND,
    COMMAND_FAILED,
    LIMIT_EXCEEDED,
    MALFORMED_FRAME,
    SESSION_CLOSED
}

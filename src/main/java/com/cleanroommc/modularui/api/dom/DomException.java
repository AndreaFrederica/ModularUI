package com.cleanroommc.modularui.api.dom;

public class DomException extends RuntimeException {

    public enum Code {
        HIERARCHY_REQUEST,
        WRONG_DOCUMENT,
        NOT_FOUND,
        STALE_NODE,
        INVALID_STATE,
        NOT_SUPPORTED,
        TRANSACTION_ABORTED
    }

    private final Code code;

    public DomException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public DomException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return this.code;
    }
}

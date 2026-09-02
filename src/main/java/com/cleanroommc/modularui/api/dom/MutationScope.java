package com.cleanroommc.modularui.api.dom;

/** Explicit DOM mutation transaction. Closing without committing aborts the whole transaction. */
public final class MutationScope implements AutoCloseable {

    private final MuiDocument document;
    private final long transactionId;
    private boolean closed;

    MutationScope(MuiDocument document, long transactionId) {
        this.document = document;
        this.transactionId = transactionId;
    }

    public MutationResult commit() {
        if (this.closed) throw new IllegalStateException("Mutation scope is already closed");
        this.closed = true;
        return this.document.finishMutation(this, this.transactionId, true);
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.document.finishMutation(this, this.transactionId, false);
    }
}

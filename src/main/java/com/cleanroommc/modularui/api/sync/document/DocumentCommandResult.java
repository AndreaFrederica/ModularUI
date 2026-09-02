package com.cleanroommc.modularui.api.sync.document;

import org.jetbrains.annotations.Nullable;

/** Immutable response to one explicit client command. */
public final class DocumentCommandResult {

    private final long revision;
    private final Object payload;

    public DocumentCommandResult(long revision, @Nullable Object payload) {
        if (revision < 0) throw new IllegalArgumentException("Revision must not be negative");
        this.revision = revision;
        this.payload = payload;
    }

    public long getRevision() { return this.revision; }
    public @Nullable Object getPayload() { return this.payload; }
}

package com.cleanroommc.modularui.api.sync.document;

import java.util.Map;

/** One live authoritative endpoint instance owned by a document session. */
public interface DocumentEndpointSubscription extends AutoCloseable {

    long getRevision();

    Map<String, ?> snapshot();

    Object executeCommand(String commandKey, Object payload) throws DocumentSyncException;

    @Override
    void close();
}

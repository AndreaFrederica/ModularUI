package com.cleanroommc.modularui.api.sync.document;

import java.util.Map;

/** Receives authoritative endpoint changes. Calls must remain on the owning server thread. */
public interface DocumentEndpointObserver {

    void patch(long baseRevision, long revision, DocumentPatch patch);

    void reset(long revision, Map<String, ?> snapshot);
}

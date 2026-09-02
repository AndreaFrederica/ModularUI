package com.cleanroommc.modularui.api.state;

import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable change notification delivered after one store mutation batch. */
public final class MuiStoreChange {

    private final MuiStore store;
    private final long revision;
    private final Set<String> changedKeys;
    private final Map<String, Object> previous;
    private final Map<String, Object> current;

    MuiStoreChange(MuiStore store, long revision, Set<String> changedKeys,
                   Map<String, Object> previous, Map<String, Object> current) {
        this.store = store;
        this.revision = revision;
        this.changedKeys = Collections.unmodifiableSet(new LinkedHashSet<>(changedKeys));
        this.previous = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(previous));
        this.current = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(current));
    }

    public MuiStore getStore() { return this.store; }
    public long getRevision() { return this.revision; }
    public @UnmodifiableView Set<String> getChangedKeys() { return this.changedKeys; }
    public @UnmodifiableView Map<String, Object> getPrevious() { return this.previous; }
    public @UnmodifiableView Map<String, Object> getCurrent() { return this.current; }
}

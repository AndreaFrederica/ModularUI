package com.cleanroommc.modularui.api.navigation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable capture of the open panel and widget tree. */
public final class NavigationTreeView {

    private final long structureRevision;
    private final long geometryRevision;
    private final List<String> roots;
    private final Map<String, NavigationTreeEntry> entries;
    private final String activeScope;

    public NavigationTreeView(long structureRevision, long geometryRevision, List<String> roots,
                              Collection<NavigationTreeEntry> entries, String activeScope) {
        this.structureRevision = structureRevision;
        this.geometryRevision = geometryRevision;
        this.roots = Collections.unmodifiableList(new ArrayList<>(roots));
        Map<String, NavigationTreeEntry> map = new LinkedHashMap<>();
        for (NavigationTreeEntry entry : entries) map.put(entry.getPath(), entry);
        this.entries = Collections.unmodifiableMap(map);
        this.activeScope = activeScope;
    }

    public long getStructureRevision() { return this.structureRevision; }
    public long getGeometryRevision() { return this.geometryRevision; }
    public List<String> getRoots() { return this.roots; }
    public Collection<NavigationTreeEntry> getEntries() { return this.entries.values(); }
    public NavigationTreeEntry getEntry(String path) { return this.entries.get(path); }
    public String getActiveScope() { return this.activeScope; }
}

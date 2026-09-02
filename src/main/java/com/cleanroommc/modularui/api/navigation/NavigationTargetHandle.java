package com.cleanroommc.modularui.api.navigation;

import com.cleanroommc.modularui.api.dom.NodeHandle;

import java.util.Objects;

/** Stable DOM identity with a legacy path fallback for non-adopted navigation trees. */
public final class NavigationTargetHandle {

    private final String legacyPath;
    private final NodeHandle nodeHandle;

    public NavigationTargetHandle(String legacyPath, NodeHandle nodeHandle) {
        this.legacyPath = Objects.requireNonNull(legacyPath, "legacyPath");
        this.nodeHandle = Objects.requireNonNull(nodeHandle, "nodeHandle");
    }

    public String getLegacyPath() {
        return this.legacyPath;
    }

    public NodeHandle getNodeHandle() {
        return this.nodeHandle;
    }

    public boolean hasDomIdentity() {
        return this.nodeHandle.isPresent();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NavigationTargetHandle other)) return false;
        return this.legacyPath.equals(other.legacyPath) && this.nodeHandle.equals(other.nodeHandle);
    }

    @Override
    public int hashCode() {
        return 31 * this.legacyPath.hashCode() + this.nodeHandle.hashCode();
    }

    @Override
    public String toString() {
        return hasDomIdentity() ? this.nodeHandle.toString() : this.legacyPath;
    }
}

package com.cleanroommc.modularui.api.navigation;

import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One immutable entry in a captured ModularUI widget tree. */
public final class NavigationTreeEntry {

    private final String path;
    private final NavigationTargetHandle targetHandle;
    @Nullable private final String parentPath;
    private final List<String> children;
    private final IWidget widget;
    private final NavigationInfo info;
    private final NavigationGeometry geometry;
    private final boolean enabled;

    public NavigationTreeEntry(String path, @Nullable String parentPath, List<String> children,
                               IWidget widget, NavigationInfo info, NavigationGeometry geometry,
                               boolean enabled) {
        this(path, parentPath, children, widget, info, geometry, enabled,
                new NavigationTargetHandle(path, com.cleanroommc.modularui.api.dom.NodeHandle.EMPTY));
    }

    public NavigationTreeEntry(String path, @Nullable String parentPath, List<String> children,
                               IWidget widget, NavigationInfo info, NavigationGeometry geometry,
                               boolean enabled, NavigationTargetHandle targetHandle) {
        this.path = path;
        this.targetHandle = Objects.requireNonNull(targetHandle, "targetHandle");
        this.parentPath = parentPath;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
        this.widget = widget;
        this.info = info;
        this.geometry = geometry;
        this.enabled = enabled;
    }

    public String getPath() { return this.path; }
    public NavigationTargetHandle getTargetHandle() { return this.targetHandle; }
    @Nullable public String getParentPath() { return this.parentPath; }
    public List<String> getChildren() { return this.children; }
    public IWidget getWidget() { return this.widget; }
    public NavigationInfo getInfo() { return this.info; }
    public NavigationGeometry getGeometry() { return this.geometry; }
    public boolean isEnabled() { return this.enabled; }
}

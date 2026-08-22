package com.cleanroommc.modularui.api.navigation;

/** Result returned by a semantic navigation action. */
public enum NavigationActionResult {
    IGNORED(false, false),
    HANDLED(true, false),
    CHANGED(true, true),
    STALE(false, false),
    REJECTED(false, false);

    private final boolean handled;
    private final boolean changed;

    NavigationActionResult(boolean handled, boolean changed) {
        this.handled = handled;
        this.changed = changed;
    }

    public boolean isHandled() {
        return this.handled;
    }

    public boolean isChanged() {
        return this.changed;
    }
}

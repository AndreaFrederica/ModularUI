package com.cleanroommc.modularui.api.event;

import java.util.Objects;

/** A typed event key. Instances are compared by identity. */
public final class MuiEventType<E extends MuiEvent> {

    private final String name;
    private final boolean bubbles;
    private final boolean cancelable;

    public MuiEventType(String name, boolean bubbles, boolean cancelable) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isEmpty()) throw new IllegalArgumentException("Event type name must not be empty");
        this.bubbles = bubbles;
        this.cancelable = cancelable;
    }

    public String getName() {
        return this.name;
    }

    public boolean bubbles() {
        return this.bubbles;
    }

    public boolean isCancelable() {
        return this.cancelable;
    }

    @Override
    public String toString() {
        return this.name;
    }
}

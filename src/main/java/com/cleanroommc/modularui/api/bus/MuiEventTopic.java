package com.cleanroommc.modularui.api.bus;

import java.util.Objects;

/** Identity-based typed topic for the UI-independent application event bus. */
public final class MuiEventTopic<T> {

    private final String id;
    private final Class<T> eventType;

    public MuiEventTopic(String id, Class<T> eventType) {
        this.id = Objects.requireNonNull(id, "id").trim();
        if (this.id.isEmpty()) throw new IllegalArgumentException("Topic id must not be empty");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    public String getId() { return this.id; }
    public Class<T> getEventType() { return this.eventType; }

    @Override
    public String toString() { return this.id; }
}

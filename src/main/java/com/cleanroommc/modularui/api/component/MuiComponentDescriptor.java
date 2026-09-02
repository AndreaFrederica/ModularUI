package com.cleanroommc.modularui.api.component;

import java.util.Objects;

/** Immutable manifest entry describing an XML component template resource. */
public final class MuiComponentDescriptor {

    private final String name;
    private final String resourceId;

    private MuiComponentDescriptor(Builder builder) {
        this.name = normalizeName(builder.name);
        this.resourceId = normalizeResource(builder.resourceId);
    }

    public static Builder builder(String name, String resourceId) {
        return new Builder(name, resourceId);
    }

    public String getName() {
        return this.name;
    }

    public String getResourceId() {
        return this.resourceId;
    }

    private static String normalizeName(String value) {
        String normalized = Objects.requireNonNull(value, "name").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("name must not be empty");
        return normalized;
    }

    private static String normalizeResource(String value) {
        String normalized = Objects.requireNonNull(value, "resourceId").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("resourceId must not be empty");
        return normalized;
    }

    public static final class Builder {
        private final String name;
        private final String resourceId;

        private Builder(String name, String resourceId) {
            this.name = name;
            this.resourceId = resourceId;
        }

        public MuiComponentDescriptor build() {
            return new MuiComponentDescriptor(this);
        }
    }
}

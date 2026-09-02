package com.cleanroommc.modularui.api.markup;

import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable manifest data used to authorize component and stylesheet resources for one application owner. */
public final class MuiApplicationDescriptor {

    private final String owner;
    private final Map<String, String> namespaces;
    private final Map<String, String> components;
    private final List<String> stylesheets;

    private MuiApplicationDescriptor(Builder builder) {
        this.owner = normalize(builder.owner, "owner");
        this.namespaces = immutableMap(builder.namespaces);
        this.components = immutableMap(builder.components);
        this.stylesheets = Collections.unmodifiableList(new ArrayList<>(builder.stylesheets));
    }

    public static Builder builder(String owner) {
        return new Builder(owner);
    }

    public String getOwner() {
        return this.owner;
    }

    public @UnmodifiableView Map<String, String> getNamespaces() {
        return this.namespaces;
    }

    public @UnmodifiableView Map<String, String> getComponents() {
        return this.components;
    }

    public @UnmodifiableView List<String> getStylesheets() {
        return this.stylesheets;
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            copy.put(normalize(entry.getKey(), "manifest key"), normalize(entry.getValue(), "manifest resource"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        return normalized;
    }

    public static final class Builder {
        private final String owner;
        private final Map<String, String> namespaces = new LinkedHashMap<>();
        private final Map<String, String> components = new LinkedHashMap<>();
        private final List<String> stylesheets = new ArrayList<>();

        private Builder(String owner) {
            this.owner = owner;
        }

        public Builder namespace(String prefix, String uri) {
            String key = normalize(prefix, "namespace prefix");
            if (this.namespaces.putIfAbsent(key, normalize(uri, "namespace URI")) != null) {
                throw new IllegalArgumentException("Duplicate namespace prefix: " + key);
            }
            return this;
        }

        public Builder component(String name, String resourceId) {
            String key = normalize(name, "component name");
            if (this.components.putIfAbsent(key, normalize(resourceId, "component resource")) != null) {
                throw new IllegalArgumentException("Duplicate component name: " + key);
            }
            return this;
        }

        public Builder stylesheet(String resourceId) {
            this.stylesheets.add(normalize(resourceId, "stylesheet resource"));
            return this;
        }

        public MuiApplicationDescriptor build() {
            return new MuiApplicationDescriptor(this);
        }
    }
}

package com.cleanroommc.modularui.style;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Registry for typed style properties; Widget/theme adapters can be added without changing the cascade. */
public final class MuiStylePropertyRegistry {
    private final Map<String, Descriptor<?>> descriptors = new LinkedHashMap<>();
    private boolean frozen;

    public <T> void register(String name, Function<JsonElement, T> parser, boolean inherited, @Nullable T initialValue) {
        if (frozen) throw new IllegalStateException("Style property registry is frozen");
        String key = normalize(name);
        if (descriptors.containsKey(key)) throw new IllegalArgumentException("Style property is already registered: " + key);
        descriptors.put(key, new Descriptor<>(parser, inherited, initialValue));
    }

    public @Nullable Descriptor<?> find(String name) { return descriptors.get(normalize(name)); }
    public void freeze() { frozen = true; }
    public boolean isFrozen() { return frozen; }

    public static final class Descriptor<T> {
        private final Function<JsonElement, T> parser;
        private final boolean inherited;
        private final T initialValue;
        private Descriptor(Function<JsonElement, T> parser, boolean inherited, T initialValue) {
            this.parser = Objects.requireNonNull(parser, "parser");
            this.inherited = inherited;
            this.initialValue = initialValue;
        }
        public T parse(JsonElement value) { return parser.apply(value); }
        public boolean isInherited() { return inherited; }
        public T getInitialValue() { return initialValue; }
    }

    private static String normalize(String name) {
        String value = Objects.requireNonNull(name, "name").trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Style property name must not be empty");
        return value;
    }
}

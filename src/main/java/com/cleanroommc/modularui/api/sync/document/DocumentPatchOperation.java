package com.cleanroommc.modularui.api.sync.document;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** One top-level state mutation. Top-level keys match the Java MuiStore model. */
public final class DocumentPatchOperation {

    public enum Type { SET, REMOVE }

    private final Type type;
    private final String key;
    private final Object value;

    private DocumentPatchOperation(Type type, String key, @Nullable Object value) {
        this.type = Objects.requireNonNull(type, "type");
        this.key = normalizeKey(key);
        this.value = value;
    }

    public static DocumentPatchOperation set(String key, @Nullable Object value) {
        return new DocumentPatchOperation(Type.SET, key, value);
    }

    public static DocumentPatchOperation remove(String key) {
        return new DocumentPatchOperation(Type.REMOVE, key, null);
    }

    public Type getType() { return this.type; }
    public String getKey() { return this.key; }
    public @Nullable Object getValue() { return this.value; }

    private static String normalizeKey(String key) {
        String value = Objects.requireNonNull(key, "key").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Patch key must not be empty");
        return value;
    }
}

package com.cleanroommc.modularui.style;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable result of stylesheet cascade for one DOM element. */
public final class MuiComputedStyle {
    private final Map<String, JsonElement> properties;

    MuiComputedStyle(Map<String, JsonElement> properties) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public @UnmodifiableView Map<String, JsonElement> getProperties() { return this.properties; }
    public boolean has(String name) { return this.properties.containsKey(normalize(name)); }
    public @Nullable JsonElement get(String name) { return this.properties.get(normalize(name)); }
    public @Nullable String getString(String name) {
        JsonElement value = get(name);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }
    public int getInt(String name, int fallback) {
        JsonElement value = get(name);
        return value == null || !value.isJsonPrimitive() ? fallback : value.getAsInt();
    }
    public boolean getBoolean(String name, boolean fallback) {
        JsonElement value = get(name);
        return value == null || !value.isJsonPrimitive() ? fallback : value.getAsBoolean();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

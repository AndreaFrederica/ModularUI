package com.cleanroommc.modularui.api.component;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-screen registry of explicitly permitted native DOM tags. */
public final class MuiElementRegistry {

    private final Map<String, MuiElementDescriptor<?>> descriptors = new LinkedHashMap<>();
    private boolean frozen;

    public void register(MuiElementDescriptor<?> descriptor) {
        if (this.frozen) throw new IllegalStateException("Element registry is frozen for this screen");
        Objects.requireNonNull(descriptor, "descriptor");
        if (this.descriptors.putIfAbsent(descriptor.getTagName(), descriptor) != null) {
            throw new IllegalArgumentException("Element tag is already registered: " + descriptor.getTagName());
        }
    }

    public @Nullable MuiElementDescriptor<?> find(String tagName) {
        return this.descriptors.get(normalize(tagName));
    }

    public MuiElementDescriptor<?> require(String tagName) {
        MuiElementDescriptor<?> descriptor = find(tagName);
        if (descriptor == null) throw new IllegalArgumentException("Element tag is not registered: " + tagName);
        return descriptor;
    }

    public @UnmodifiableView Collection<MuiElementDescriptor<?>> getDescriptors() {
        return Collections.unmodifiableCollection(this.descriptors.values());
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    public void freeze() {
        this.frozen = true;
    }

    private static String normalize(String tagName) {
        String normalized = Objects.requireNonNull(tagName, "tagName").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Tag name must not be empty");
        return normalized;
    }
}

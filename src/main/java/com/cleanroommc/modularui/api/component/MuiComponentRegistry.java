package com.cleanroommc.modularui.api.component;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registry of XML component templates. Registration is closed before a document is compiled. */
public final class MuiComponentRegistry {

    private final Map<String, MuiComponentDescriptor> descriptors = new LinkedHashMap<>();
    private boolean frozen;

    public void register(MuiComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (this.frozen) throw new IllegalStateException("Component registry is frozen");
        if (this.descriptors.putIfAbsent(descriptor.getName(), descriptor) != null) {
            throw new IllegalArgumentException("Component is already registered: " + descriptor.getName());
        }
    }

    public void contribute(MuiComponentContributor contributor) {
        Objects.requireNonNull(contributor, "contributor").contribute(this);
    }

    public @Nullable MuiComponentDescriptor find(String name) {
        return this.descriptors.get(normalize(name));
    }

    public MuiComponentDescriptor require(String name) {
        MuiComponentDescriptor descriptor = find(name);
        if (descriptor == null) throw new IllegalArgumentException("Component is not registered: " + name);
        return descriptor;
    }

    public @UnmodifiableView Collection<MuiComponentDescriptor> getDescriptors() {
        return Collections.unmodifiableCollection(this.descriptors.values());
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    public void freeze() {
        this.frozen = true;
    }

    private static String normalize(String name) {
        String value = Objects.requireNonNull(name, "name").trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Component name must not be empty");
        return value;
    }
}

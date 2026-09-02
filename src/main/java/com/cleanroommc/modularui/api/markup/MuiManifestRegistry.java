package com.cleanroommc.modularui.api.markup;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit application manifest registry. Registration is closed before resources are compiled. */
public final class MuiManifestRegistry {

    private final Map<String, MuiApplicationDescriptor> descriptors = new LinkedHashMap<>();
    private boolean frozen;

    public void register(MuiApplicationDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (this.frozen) throw new IllegalStateException("Manifest registry is frozen");
        if (this.descriptors.putIfAbsent(descriptor.getOwner(), descriptor) != null) {
            throw new IllegalArgumentException("Manifest owner is already registered: " + descriptor.getOwner());
        }
    }

    public @Nullable MuiApplicationDescriptor find(String owner) {
        return this.descriptors.get(Objects.requireNonNull(owner, "owner").trim());
    }

    public MuiApplicationDescriptor require(String owner) {
        MuiApplicationDescriptor descriptor = find(owner);
        if (descriptor == null) throw new IllegalArgumentException("Manifest owner is not registered: " + owner);
        return descriptor;
    }

    public @UnmodifiableView Collection<MuiApplicationDescriptor> getDescriptors() {
        return Collections.unmodifiableCollection(this.descriptors.values());
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    public void freeze() {
        this.frozen = true;
    }
}

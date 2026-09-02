package com.cleanroommc.modularui.api.sync.document;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Server-owned endpoint registry. Registration is closed when the first handler freezes it. */
public final class DocumentEndpointCatalog {

    private final Map<String, DocumentEndpointDescriptor> endpoints = new LinkedHashMap<>();
    private boolean frozen;

    public synchronized DocumentEndpointCatalog register(DocumentEndpointDescriptor descriptor) {
        if (this.frozen) throw new IllegalStateException("Document endpoint catalog is frozen");
        Objects.requireNonNull(descriptor, "descriptor");
        DocumentEndpointDescriptor previous = this.endpoints.putIfAbsent(descriptor.getKey(), descriptor);
        if (previous != null && previous != descriptor) {
            throw new IllegalArgumentException("Duplicate document endpoint: " + descriptor.getKey());
        }
        return this;
    }

    public DocumentEndpointCatalog register(String key, int schemaVersion, DocumentEndpoint endpoint) {
        return register(DocumentEndpointDescriptor.builder(key, schemaVersion, endpoint).build());
    }

    public synchronized DocumentEndpointCatalog freeze() {
        this.frozen = true;
        return this;
    }

    public synchronized boolean isFrozen() { return this.frozen; }

    public synchronized @Nullable DocumentEndpointDescriptor find(String key) {
        return this.endpoints.get(DocumentEndpointDescriptor.normalizeKey(key));
    }

    public synchronized Map<String, DocumentEndpointDescriptor> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.endpoints));
    }
}

package com.cleanroommc.modularui.api.sync;

import java.util.Objects;

/** One canonical server/client UI protocol topology entry. */
public final class MuiProtocolEntry {

    public enum Kind { HANDLER, ACTION, SLOT }

    private final Kind kind;
    private final String key;
    private final String type;
    private final int version;
    private final int numericId;
    private final int order;

    public MuiProtocolEntry(Kind kind, String key, String type, int version, int order) {
        this(kind, key, type, version, kind == Kind.ACTION ? -1 : 0, order);
    }

    public MuiProtocolEntry(Kind kind, String key, String type, int version, int numericId, int order) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = requireId(key, "key");
        this.type = requireId(type, "type");
        if (version < 0) throw new IllegalArgumentException("Protocol entry version must not be negative");
        if (kind == Kind.ACTION && numericId != -1) throw new IllegalArgumentException("Protocol actions do not have a numeric id");
        if (kind != Kind.ACTION && numericId < 0) throw new IllegalArgumentException("Protocol sync id must not be negative");
        if (order < 0) throw new IllegalArgumentException("Protocol entry order must not be negative");
        this.version = version;
        this.numericId = numericId;
        this.order = order;
    }

    public Kind getKind() { return this.kind; }
    public String getKey() { return this.key; }
    public String getType() { return this.type; }
    public int getVersion() { return this.version; }
    public int getNumericId() { return this.numericId; }
    public int getOrder() { return this.order; }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Protocol entry " + label + " must not be empty");
        return normalized;
    }
}

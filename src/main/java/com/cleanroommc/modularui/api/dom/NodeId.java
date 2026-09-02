package com.cleanroommc.modularui.api.dom;

public final class NodeId {

    private final long value;

    public NodeId(long value) {
        if (value <= 0) throw new IllegalArgumentException("node id must be positive");
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NodeId other && this.value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}

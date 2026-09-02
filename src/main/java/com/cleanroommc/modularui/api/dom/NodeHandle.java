package com.cleanroommc.modularui.api.dom;

/** Stable identity for one node during one document generation. */
public final class NodeHandle {

    public static final NodeHandle EMPTY = new NodeHandle(0, 0, 0);

    private final long documentId;
    private final long generation;
    private final long nodeId;

    public NodeHandle(long documentId, long generation, long nodeId) {
        if (documentId < 0 || generation < 0 || nodeId < 0) {
            throw new IllegalArgumentException("handle values must be non-negative");
        }
        this.documentId = documentId;
        this.generation = generation;
        this.nodeId = nodeId;
    }

    public boolean isPresent() {
        return this.documentId != 0 && this.generation != 0 && this.nodeId != 0;
    }

    public long getDocumentId() { return this.documentId; }
    public long getGeneration() { return this.generation; }
    public long getNodeId() { return this.nodeId; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NodeHandle other)) return false;
        return this.documentId == other.documentId && this.generation == other.generation
                && this.nodeId == other.nodeId;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(this.documentId);
        result = 31 * result + Long.hashCode(this.generation);
        return 31 * result + Long.hashCode(this.nodeId);
    }

    @Override
    public String toString() {
        return isPresent() ? this.documentId + ":" + this.generation + ":" + this.nodeId : "empty";
    }
}

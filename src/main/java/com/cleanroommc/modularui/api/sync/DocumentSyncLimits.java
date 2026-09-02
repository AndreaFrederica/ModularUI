package com.cleanroommc.modularui.api.sync;

/** Immutable resource limits shared by document sync clients, servers and codecs. */
public final class DocumentSyncLimits {

    public static final DocumentSyncLimits DEFAULT = new DocumentSyncLimits(
            256 * 1024, 32 * 1024 - 1, 4096, 32, 128, 256, 4096);

    private final int maxFrameBytes;
    private final int maxStringBytes;
    private final int maxCollectionEntries;
    private final int maxNestingDepth;
    private final int maxActiveChannels;
    private final int maxPendingCommands;
    private final int maxPatchOperations;

    public DocumentSyncLimits(int maxFrameBytes, int maxStringBytes, int maxCollectionEntries,
                              int maxNestingDepth, int maxActiveChannels, int maxPendingCommands,
                              int maxPatchOperations) {
        this.maxFrameBytes = positive(maxFrameBytes, "maxFrameBytes");
        this.maxStringBytes = positive(maxStringBytes, "maxStringBytes");
        this.maxCollectionEntries = positive(maxCollectionEntries, "maxCollectionEntries");
        this.maxNestingDepth = positive(maxNestingDepth, "maxNestingDepth");
        this.maxActiveChannels = positive(maxActiveChannels, "maxActiveChannels");
        this.maxPendingCommands = positive(maxPendingCommands, "maxPendingCommands");
        this.maxPatchOperations = positive(maxPatchOperations, "maxPatchOperations");
    }

    public int getMaxFrameBytes() { return this.maxFrameBytes; }
    public int getMaxStringBytes() { return this.maxStringBytes; }
    public int getMaxCollectionEntries() { return this.maxCollectionEntries; }
    public int getMaxNestingDepth() { return this.maxNestingDepth; }
    public int getMaxActiveChannels() { return this.maxActiveChannels; }
    public int getMaxPendingCommands() { return this.maxPendingCommands; }
    public int getMaxPatchOperations() { return this.maxPatchOperations; }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}

package com.cleanroommc.modularui.api.dom;

public final class MutationResult {

    public static final MutationResult PENDING = new MutationResult(false, 0);

    private final boolean committed;
    private final int operationCount;

    public MutationResult(boolean committed, int operationCount) {
        this.committed = committed;
        this.operationCount = operationCount;
    }

    public boolean isCommitted() {
        return this.committed;
    }

    public int getOperationCount() {
        return this.operationCount;
    }
}

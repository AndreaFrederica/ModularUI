package com.cleanroommc.modularui.api.dom;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Immutable, validated structural change consumed by a document projection host. */
@ApiStatus.Internal
public final class DomMutation {

    public enum Kind { INSERT, REMOVE }

    private final Kind kind;
    private final MuiNode parent;
    private final MuiNode previousParent;
    private final MuiNode child;
    private final int index;
    private final int previousIndex;
    private final boolean previouslyRoot;
    private final boolean connectedBefore;
    private final boolean connectedAfter;

    DomMutation(Kind kind, @Nullable MuiNode parent, @Nullable MuiNode previousParent, MuiNode child,
                int index, int previousIndex, boolean previouslyRoot,
                boolean connectedBefore, boolean connectedAfter) {
        this.kind = kind;
        this.parent = parent;
        this.previousParent = previousParent;
        this.child = child;
        this.index = index;
        this.previousIndex = previousIndex;
        this.previouslyRoot = previouslyRoot;
        this.connectedBefore = connectedBefore;
        this.connectedAfter = connectedAfter;
    }

    public Kind getKind() { return this.kind; }
    public @Nullable MuiNode getParent() { return this.parent; }
    public @Nullable MuiNode getPreviousParent() { return this.previousParent; }
    public MuiNode getChild() { return this.child; }
    public int getIndex() { return this.index; }
    public int getPreviousIndex() { return this.previousIndex; }
    public boolean wasPreviouslyRoot() { return this.previouslyRoot; }
    public boolean wasConnected() { return this.connectedBefore; }
    public boolean willBeConnected() { return this.connectedAfter; }
    public boolean isMove() { return this.kind == Kind.INSERT && this.previousIndex >= 0; }
}

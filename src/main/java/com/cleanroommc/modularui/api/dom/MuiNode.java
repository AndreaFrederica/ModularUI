package com.cleanroommc.modularui.api.dom;

import com.cleanroommc.modularui.api.event.IEventTarget;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MuiNode implements IEventTarget {

    private final MuiDocument ownerDocument;
    private final NodeHandle handle;
    final List<MuiNode> mutableChildren = new ArrayList<>();
    private final List<MuiNode> children = Collections.unmodifiableList(this.mutableChildren);
    MuiNode parent;
    boolean alive = true;
    boolean connected;

    MuiNode(MuiDocument ownerDocument, NodeHandle handle) {
        this.ownerDocument = ownerDocument;
        this.handle = handle;
    }

    public MuiDocument getOwnerDocument() {
        return this.ownerDocument;
    }

    public NodeHandle getHandle() {
        return this.handle;
    }

    public NodeId getNodeId() {
        return new NodeId(this.handle.getNodeId());
    }

    public boolean isAlive() {
        return this.alive && this.ownerDocument.isOpen();
    }

    public boolean isConnected() {
        return isAlive() && this.connected;
    }

    public @Nullable MuiNode getParentNode() {
        return this.parent;
    }

    public @UnmodifiableView List<MuiNode> getChildNodes() {
        return this.children;
    }

    @Override
    public @Nullable IEventTarget getEventParent() {
        if (!isConnected()) return null;
        return this.parent == null ? this.ownerDocument : this.parent;
    }

    public MuiNode appendChild(MuiNode child) {
        return insertChild(-1, child);
    }

    public MuiNode insertBefore(MuiNode child, @Nullable MuiNode reference) {
        requireAlive();
        int index = reference == null ? this.mutableChildren.size() : this.mutableChildren.indexOf(reference);
        if (reference != null && index < 0) throw new DomException(DomException.Code.NOT_FOUND, "Reference node is not a child");
        return insertChild(index, child);
    }

    public MuiNode insertChild(int index, MuiNode child) {
        requireCanHaveChildren();
        this.ownerDocument.insert(this, child, index);
        return child;
    }

    public MuiNode removeChild(MuiNode child) {
        requireCanHaveChildren();
        this.ownerDocument.remove(this, child);
        return child;
    }

    public MuiNode replaceChild(MuiNode replacement, MuiNode child) {
        requireCanHaveChildren();
        int index = this.mutableChildren.indexOf(child);
        if (index < 0) throw new DomException(DomException.Code.NOT_FOUND, "Node is not a child");
        try (MutationScope mutation = this.ownerDocument.beginMutation()) {
            this.ownerDocument.remove(this, child);
            this.ownerDocument.insert(this, replacement, index);
            mutation.commit();
        }
        return child;
    }

    public void remove() {
        requireAlive();
        if (this.parent != null) {
            this.parent.removeChild(this);
        } else if (this.connected) {
            this.ownerDocument.removeChild(this);
        }
    }

    public String getTextContent() {
        StringBuilder result = new StringBuilder();
        appendTextContent(result);
        return result.toString();
    }

    void appendTextContent(StringBuilder result) {
        for (MuiNode child : this.mutableChildren) child.appendTextContent(result);
    }

    void requireAlive() {
        if (!isAlive()) throw new DomException(DomException.Code.STALE_NODE, "Node handle is stale: " + this.handle);
    }

    void requireCanHaveChildren() {
        requireAlive();
        if (this instanceof MuiText) {
            throw new DomException(DomException.Code.HIERARCHY_REQUEST, "Text nodes cannot have children");
        }
    }
}

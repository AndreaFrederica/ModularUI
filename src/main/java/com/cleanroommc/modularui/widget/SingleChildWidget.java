package com.cleanroommc.modularui.widget;

import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.List;

public class SingleChildWidget<W extends SingleChildWidget<W>> extends Widget<W> {

    private IWidget child;
    private List<IWidget> list = Collections.emptyList();

    public IWidget getChild() {
        return child;
    }

    @Override
    public @NotNull List<IWidget> getChildren() {
        return this.list;
    }

    private void updateList() {
        this.list = this.child == null ? Collections.emptyList() : Collections.singletonList(this.child);
    }

    public W child(IWidget child) {
        if (child == this || this.child == child) {
            return getThis();
        }
        IWidget previous = this.child;
        boolean mounted = isValid();
        if (previous != null && mounted) {
            previous.dispose();
        }
        this.child = child;
        updateList();
        if (child != null && mounted) {
            child.initialise(this, true);
        }
        if (child != null) onChildAdd(child);
        if (mounted) {
            markNavigationStructureDirty();
            scheduleResize();
            if (previous != null) getScreen().getDocumentController().onLegacyChildRemoved(this, previous);
            if (child != null) getScreen().getDocumentController().onLegacyChildAdded(this, child, 0);
        }
        return getThis();
    }

    @ApiStatus.Internal
    public boolean supportsDomChildMutations() {
        return true;
    }

    @ApiStatus.Internal
    public boolean canAcceptDomChild(IWidget child) {
        return child != null && child != this && (this.child == null || this.child == child);
    }

    @ApiStatus.Internal
    public void insertDomChild(IWidget child, int index) {
        if (index != 0 || !canAcceptDomChild(child)) throw new IllegalStateException("Single child parent is full");
        child(child);
    }

    @ApiStatus.Internal
    public void removeDomChild(IWidget child) {
        if (this.child != child) throw new IllegalStateException("Widget is not the current child");
        child(null);
    }

    @ApiStatus.Internal
    public void detachDomChildForMove(IWidget child) {
        if (this.child != child) throw new IllegalStateException("Widget is not the current child");
        this.child = null;
        updateList();
        if (isValid()) markNavigationStructureDirty();
    }

    @ApiStatus.Internal
    public void attachMovedDomChild(IWidget child, int index) {
        if (index != 0 || this.child != null) throw new IllegalStateException("Single child parent is full");
        this.child = child;
        updateList();
        if (child instanceof AbstractWidget widget) widget.reparentInternal(this);
        else throw new IllegalStateException("A third-party IWidget cannot be reparented without remounting");
        onChildAdd(child);
        if (isValid()) markNavigationStructureDirty();
    }

    protected void onChildAdd(IWidget child) {}
}

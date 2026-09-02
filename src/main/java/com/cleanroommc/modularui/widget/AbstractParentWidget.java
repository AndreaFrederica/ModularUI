package com.cleanroommc.modularui.widget;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.VoidWidget;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A widget which can hold any amount of children.
 *
 * @param <I> type of children (in most cases just {@link IWidget}). Use {@link VoidWidget} if no children should be added.
 * @param <W> type of this widget
 */
public class AbstractParentWidget<I extends IWidget, W extends AbstractParentWidget<I, W>> extends Widget<W> {

    private final List<I> children = new ArrayList<>();
    private final List<I> childrenView = Collections.unmodifiableList(this.children);

    /**
     * A read-only view of all children of this widget.
     *
     * @return a view of all children.
     */
    @SuppressWarnings("unchecked")
    @UnmodifiableView
    @NotNull
    @Override
    public List<IWidget> getChildren() {
        return (List<IWidget>) (List<?>) this.childrenView;
    }

    /**
     * A read-only view of all children of this widget with the given children type {@link I}.
     *
     * @return a view of all children.
     */
    @UnmodifiableView
    @NotNull
    public List<I> getTypeChildren() {
        return this.childrenView;
    }

    protected final List<I> mutableChildren() {
        return this.children;
    }

    @Override
    public boolean canHover() {
        if (IDrawable.isVisible(getBackground()) ||
                IDrawable.isVisible(getHoverBackground()) ||
                IDrawable.isVisible(getHoverOverlay()) ||
                getTooltip() != null) return true;
        WidgetThemeEntry<?> widgetTheme = getWidgetTheme(getPanel().getTheme());
        if (getBackground() == null && IDrawable.isVisible(widgetTheme.getTheme().getBackground())) return true;
        return getHoverBackground() == null && IDrawable.isVisible(widgetTheme.getHoverTheme().getBackground());
    }

    @Override
    public boolean canClickThrough() {
        return !canHover();
    }

    @Override
    public boolean canHoverThrough() {
        return !canHover();
    }

    protected boolean addChild(I child, int index) {
        if (child == null || child == this || getChildren().contains(child)) {
            return false;
        }
        if (child instanceof ModularPanel) {
            throw new IllegalArgumentException("ModularPanel should not be added as child widget; Use ModularScreen#openPanel instead");
        }
        if (!isChildValid(child)) {
            throw new IllegalArgumentException("Child '" + child + "' is not valid for parent '" + this + "'!");
        }
        if (index < 0) {
            index += getChildren().size() + 1;
        }
        this.children.add(index, child);
        if (isValid()) {
            child.initialise(this, true);
        }
        onChildAdd(child);
        markNavigationStructureDirty();
        if (isValid()) getScreen().getDocumentController().onLegacyChildAdded(this, child, this.children.indexOf(child));
        return true;
    }

    protected boolean remove(I child) {
        if (this.children.remove(child)) {
            if (isValid()) child.dispose();
            onChildRemove(child);
            markNavigationStructureDirty();
            if (isValid()) getScreen().getDocumentController().onLegacyChildRemoved(this, child);
            return true;
        }
        return false;
    }

    protected boolean remove(int index) {
        if (index < 0) {
            index = getChildren().size() + index + 1;
        }
        I child = this.children.remove(index);
        if (isValid()) child.dispose();
        onChildRemove(child);
        markNavigationStructureDirty();
        if (isValid()) getScreen().getDocumentController().onLegacyChildRemoved(this, child);
        return true;
    }

    protected boolean removeAll() {
        if (this.children.isEmpty()) return false;
        List<I> removed = new ArrayList<>(this.children);
        for (I i : removed) {
            if (isValid()) i.dispose();
            onChildRemove(i);
        }
        this.children.clear();
        markNavigationStructureDirty();
        if (isValid()) {
            for (I child : removed) getScreen().getDocumentController().onLegacyChildRemoved(this, child);
        }
        return true;
    }

    protected boolean move(int from, int to) {
        if (from < 0 || from >= this.children.size() || to < 0 || to >= this.children.size()) return false;
        if (from == to) return true;
        I child = this.children.remove(from);
        this.children.add(to, child);
        markNavigationStructureDirty();
        if (isValid()) getScreen().getDocumentController().onLegacyChildMoved(this, child, to);
        return true;
    }

    @ApiStatus.Internal
    public boolean supportsDomChildMutations() {
        return true;
    }

    @ApiStatus.Internal
    public boolean canAcceptDomChild(IWidget child) {
        return !getChildren().contains(child) && isDomChildTypeValid(child);
    }

    @ApiStatus.Internal
    public boolean isDomChildTypeValid(IWidget child) {
        if (child == null || child == this || child instanceof ModularPanel) return false;
        try {
            @SuppressWarnings("unchecked") I typedChild = (I) child;
            return isChildValid(typedChild);
        } catch (ClassCastException ignored) {
            return false;
        }
    }

    @ApiStatus.Internal
    public void insertDomChild(IWidget child, int index) {
        @SuppressWarnings("unchecked") I typedChild = (I) child;
        if (!addChild(typedChild, index)) throw new IllegalStateException("Failed to insert DOM child");
    }

    @ApiStatus.Internal
    public void removeDomChild(IWidget child) {
        @SuppressWarnings("unchecked") I typedChild = (I) child;
        if (!remove(typedChild)) throw new IllegalStateException("Failed to remove DOM child");
    }

    @ApiStatus.Internal
    public void moveDomChild(int from, int to) {
        if (!move(from, to)) throw new IllegalStateException("Failed to move DOM child");
    }

    @ApiStatus.Internal
    public void detachDomChildForMove(IWidget child) {
        @SuppressWarnings("unchecked") I typedChild = (I) child;
        if (!this.children.remove(typedChild)) throw new IllegalStateException("Failed to detach DOM child");
        onChildRemove(typedChild);
        markNavigationStructureDirty();
    }

    @ApiStatus.Internal
    public void attachMovedDomChild(IWidget child, int index) {
        @SuppressWarnings("unchecked") I typedChild = (I) child;
        this.children.add(index, typedChild);
        if (typedChild instanceof AbstractWidget widget) {
            widget.reparentInternal(this);
        } else {
            throw new IllegalStateException("A third-party IWidget cannot be reparented without remounting");
        }
        onChildAdd(typedChild);
        markNavigationStructureDirty();
    }

    protected boolean isChildValid(I child) {
        return true;
    }

    protected void onChildAdd(I child) {}

    protected void onChildRemove(I child) {}
}

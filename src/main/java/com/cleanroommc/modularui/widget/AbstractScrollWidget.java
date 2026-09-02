package com.cleanroommc.modularui.widget;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.api.navigation.INavigationActionHandler;
import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.navigation.NavigationActionResult;
import com.cleanroommc.modularui.api.navigation.NavigationAxis;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.navigation.NavigationRole;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.ScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Area;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A scrollable parent widget. Children can be added.
 *
 * @param <I> type of children (in most cases just {@link IWidget})
 * @param <W> type of this widget
 */
public abstract class AbstractScrollWidget<I extends IWidget, W extends AbstractScrollWidget<I, W>> extends AbstractParentWidget<I, W>
        implements IViewport, Interactable, INavigationActionHandler {

    private final ScrollArea scroll = new ScrollArea();
    private boolean scrollXActive, scrollYActive;

    private boolean showScrollShadows = true;
    private int lastNavigationScrollX;
    private int lastNavigationScrollY;

    @Override
    protected NavigationInfo getDefaultNavigationInfo() {
        NavigationAxis axis = this.scroll.getScrollY() != null ? NavigationAxis.VERTICAL
                : this.scroll.getScrollX() != null ? NavigationAxis.HORIZONTAL : NavigationAxis.NONE;
        return NavigationInfo.builder(NavigationRole.SCROLL_VIEW)
                .actions(NavigationAction.SCROLL_UP, NavigationAction.SCROLL_DOWN)
                .primaryAxis(axis)
                .focusable(false)
                .build();
    }

    @Override
    public NavigationActionResult onNavigationAction(NavigationAction action) {
        if (action != NavigationAction.SCROLL_UP && action != NavigationAction.SCROLL_DOWN) {
            return NavigationActionResult.IGNORED;
        }
        ScrollData data = this.scroll.getScrollY() != null ? this.scroll.getScrollY() : this.scroll.getScrollX();
        if (data == null) return NavigationActionResult.IGNORED;
        int before = data.isAnimating() ? data.getAnimatingTo() : data.getScroll();
        boolean handled = this.scroll.mouseScroll(0, 0,
                action == NavigationAction.SCROLL_UP ? 1 : -1, false);
        int after = data.isAnimating() ? data.getAnimatingTo() : data.getScroll();
        if (before != after) {
            markNavigationGeometryDirty();
            return NavigationActionResult.CHANGED;
        }
        return handled ? NavigationActionResult.HANDLED : NavigationActionResult.IGNORED;
    }

    public AbstractScrollWidget(@Nullable HorizontalScrollData x, @Nullable VerticalScrollData y) {
        super();
        this.scroll.setScrollDataX(x);
        this.scroll.setScrollDataY(y);
        listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
            this.scroll.mouseReleased(getContext());
            return false;
        });
    }

    @Override
    public Area getArea() {
        return this.scroll;
    }

    public ScrollArea getScrollArea() {
        return this.scroll;
    }

    /**
     * Specialized widgets can mark an axis as required by their layout code.
     * Style adapters must not remove such an axis when applying overflow.
     */
    public boolean isScrollAxisRequired(GuiAxis axis) {
        return false;
    }

    @Override
    public void transformChildren(IViewportStack stack) {
        stack.translate(-getScrollX(), -getScrollY());
    }

    @Override
    public void getWidgetsAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y) {
        // if 'widgets.peek() == this' is true, only then this widget is hovered
        // we should require this since a stencil is applied to this widget
        if (widgets.peek() == this && !getScrollArea().isInsideScrollbarArea(x, y)) {
            IViewport.super.getWidgetsAt(stack, widgets, x, y);
        }
    }

    @Override
    public void beforeResize(boolean onOpen) {
        super.beforeResize(onOpen);
        this.scroll.applyWidgetTheme(getPanel().getTheme().getScrollbarTheme().getTheme(isHovering()));
        checkScrollbarActive(false);
        getScrollArea().getScrollPadding().scrollPaddingAll(0);
        applyAdditionalOffset(this.scroll.getScrollX());
        applyAdditionalOffset(this.scroll.getScrollY());
    }

    protected void checkScrollbarActive(boolean resizeOnChange) {
        boolean scrollYActive = this.scroll.getScrollY() != null && this.scroll.getScrollY().isScrollBarActive(getScrollArea());
        boolean scrollXActive = this.scroll.getScrollX() != null && this.scroll.getScrollX().isScrollBarActive(getScrollArea(), scrollYActive);
        if (resizeOnChange && (scrollYActive != this.scrollYActive || scrollXActive != this.scrollXActive)) {
            scheduleResize();
        }
        this.scrollXActive = scrollXActive;
        this.scrollYActive = scrollYActive;
    }

    private void applyAdditionalOffset(ScrollData data) {
        if (data != null && data.isScrollBarActive(getScrollArea())) {
            getScrollArea().getScrollPadding().scrollPadding(data.getAxis().getOther(), data.isOnAxisStart(), data.getThickness());
        }
    }

    @Override
    public boolean canHover() {
        return super.canHover() || this.scroll.isInsideScrollbarArea(getContext().getMouseX(), getContext().getMouseY());
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        ModularGuiContext context = getContext();
        if (this.scroll.mouseClicked(context)) {
            return Result.SUCCESS;
        }
        return Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        this.scroll.mouseReleased(getContext());
        return false;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        return this.scroll.mouseScroll(getContext());
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        this.scroll.drag(getContext().getMouseX(), getContext().getMouseY());
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        checkScrollbarActive(true);
        int x = getScrollX();
        int y = getScrollY();
        if (x != this.lastNavigationScrollX || y != this.lastNavigationScrollY) {
            this.lastNavigationScrollX = x;
            this.lastNavigationScrollY = y;
            markNavigationGeometryDirty();
        }
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            Stencil.applyAtZero(this.scroll, context);
        }
    }

    @Override
    public void postDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            Stencil.remove();
            WidgetThemeEntry<WidgetTheme> scrollbarTheme = getPanel().getTheme().getScrollbarTheme();
            this.scroll.drawScrollbar(context, scrollbarTheme.getTheme(isHovering()), scrollbarTheme.getTheme().getBackground());
            if (this.showScrollShadows) this.scroll.drawScrollShadow(context);
        }
    }

    public int getScrollX() {
        return this.scroll.getScrollX() != null ? this.scroll.getScrollX().getScroll() : 0;
    }

    public int getScrollY() {
        return this.scroll.getScrollY() != null ? this.scroll.getScrollY().getScroll() : 0;
    }

    public boolean isShowScrollShadows() {
        return showScrollShadows;
    }

    public W showScrollShadows(boolean showScrollShadows) {
        this.showScrollShadows = showScrollShadows;
        return getThis();
    }
}

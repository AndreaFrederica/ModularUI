package com.cleanroommc.modularui.api.navigation;

/** Screen-space bounds of a widget after viewport transforms and clipping. */
public final class NavigationGeometry {

    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final int visibleLeft;
    private final int visibleTop;
    private final int visibleRight;
    private final int visibleBottom;
    private final boolean topPanelInteractive;

    public NavigationGeometry(int left, int top, int right, int bottom,
                              int visibleLeft, int visibleTop, int visibleRight, int visibleBottom,
                              boolean topPanelInteractive) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.visibleLeft = visibleLeft;
        this.visibleTop = visibleTop;
        this.visibleRight = visibleRight;
        this.visibleBottom = visibleBottom;
        this.topPanelInteractive = topPanelInteractive;
    }

    public int getLeft() { return this.left; }
    public int getTop() { return this.top; }
    public int getRight() { return this.right; }
    public int getBottom() { return this.bottom; }
    public int getVisibleLeft() { return this.visibleLeft; }
    public int getVisibleTop() { return this.visibleTop; }
    public int getVisibleRight() { return this.visibleRight; }
    public int getVisibleBottom() { return this.visibleBottom; }
    public boolean isVisible() { return this.visibleRight > this.visibleLeft && this.visibleBottom > this.visibleTop; }
    public boolean isTopPanelInteractive() { return this.topPanelInteractive; }
}

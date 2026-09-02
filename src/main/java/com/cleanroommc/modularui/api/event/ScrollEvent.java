package com.cleanroommc.modularui.api.event;

/** A non-cancelable, non-bubbling notification emitted after a viewport scroll changes. */
public final class ScrollEvent extends MuiEvent {

    public static final MuiEventType<ScrollEvent> SCROLL = new MuiEventType<>("scroll", false, false);

    private final int oldLeft;
    private final int oldTop;
    private final int left;
    private final int top;
    private final int scrollWidth;
    private final int scrollHeight;

    public ScrollEvent(int oldLeft, int oldTop, int left, int top, int scrollWidth, int scrollHeight) {
        super(SCROLL);
        this.oldLeft = oldLeft;
        this.oldTop = oldTop;
        this.left = left;
        this.top = top;
        this.scrollWidth = scrollWidth;
        this.scrollHeight = scrollHeight;
    }

    public int getOldLeft() { return this.oldLeft; }
    public int getOldTop() { return this.oldTop; }
    public int getLeft() { return this.left; }
    public int getTop() { return this.top; }
    public int getScrollWidth() { return this.scrollWidth; }
    public int getScrollHeight() { return this.scrollHeight; }
}

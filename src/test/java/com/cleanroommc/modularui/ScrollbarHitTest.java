package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.screen.viewport.GuiViewportStack;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.utils.ObjectList;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for scrollbar hit testing. The scrollbar is not a widget, so it only wins a
 * click when {@link com.cleanroommc.modularui.widget.AbstractScrollWidget#getWidgetsAt} removes
 * the widgets it covers from the hover list. That test must use the scroll area's local
 * coordinates, not the absolute screen position of the cursor.
 */
public class ScrollbarHitTest {

    private static final int AREA_X = 200;
    private static final int AREA_Y = 40;
    private static final int AREA_W = 100;
    private static final int AREA_H = 100;
    private static final int THICKNESS = 4;

    @Test
    void scrollAreaScrollbarHitTestIsLocal() {
        ScrollArea area = area();
        assertTrue(area.isInsideScrollbarArea(AREA_W - 1, 50), "right edge is on the scrollbar");
        assertFalse(area.isInsideScrollbarArea(AREA_W, 50), "past the right edge is not on the scrollbar");
        assertFalse(area.isInsideScrollbarArea(-1, 50), "left of the area is not on the scrollbar");
        assertFalse(area.isInsideScrollbarArea(AREA_X + AREA_W - 1, 50),
                "absolute screen coordinates must not be interpreted as local ones");
    }

    @Test
    void scrollbarOverChildKeepsChildOutOfHoverList() {
        ScrollWidget<?> scroll = scrollWidget();
        ButtonWidget<?> row = row();
        scroll.child(row);

        GuiViewportStack stack = stackAt(AREA_X, AREA_Y);

        assertEquals(1, hover(scroll, stack, AREA_X + AREA_W - 1, AREA_Y + 50),
                "a click on the scrollbar must not reach the button underneath it");
        assertEquals(2, hover(scroll, stack, AREA_X + 50, AREA_Y + 50),
                "a click on the content must still reach the button");
    }

    @Test
    void scrollbarOverChildIsStillProtectedAfterScrolling() {
        ScrollWidget<?> scroll = scrollWidget();
        scroll.child(row());
        scroll.getScrollArea().getScrollY().scrollTo(scroll.getScrollArea(), 20);

        // transformChildren() translates children by -scrollY. The scrollbar itself does not move,
        // so a click on the scrollbar is still at the same screen position.
        GuiViewportStack stack = stackAt(AREA_X, AREA_Y - 20);

        assertEquals(1, hover(scroll, stack, AREA_X + AREA_W - 1, AREA_Y + 50),
                "a scroll offset must not move the scrollbar hover area");
        assertEquals(2, hover(scroll, stack, AREA_X + 50, AREA_Y + 50 - 20),
                "a scroll offset must not break content hit testing");
    }

    private static ScrollArea area() {
        ScrollArea area = new ScrollArea(AREA_X, AREA_Y, AREA_W, AREA_H);
        VerticalScrollData data = new VerticalScrollData(false, THICKNESS);
        area.setScrollDataY(data);
        data.setScrollSize(AREA_H * 2);
        return area;
    }

    private static ScrollWidget<?> scrollWidget() {
        ScrollWidget<?> scroll = new ScrollWidget<>(new VerticalScrollData(false, THICKNESS));
        scroll.getScrollArea().set(AREA_X, AREA_Y, AREA_W, AREA_H);
        scroll.getScrollArea().getScrollY().setScrollSize(AREA_H * 2);
        return scroll;
    }

    private static ButtonWidget<?> row() {
        ButtonWidget<?> row = new ButtonWidget<>();
        // Full-width row: it is drawn underneath the scrollbar, which is the case that used to
        // swallow scrollbar drags.
        row.getArea().set(AREA_X, AREA_Y, AREA_W, AREA_H);
        row.getArea().setRelativePoint(GuiAxis.X, 0);
        row.getArea().setRelativePoint(GuiAxis.Y, 0);
        return row;
    }

    private static GuiViewportStack stackAt(int x, int y) {
        GuiViewportStack stack = new GuiViewportStack();
        stack.pushMatrix();
        stack.translate(x, y);
        return stack;
    }

    private static int hover(ScrollWidget<?> scroll, GuiViewportStack stack, int x, int y) {
        HoveredWidgetList hovered = new HoveredWidgetList(ObjectList.<LocatedWidget>create());
        hovered.add(scroll, stack, null);
        scroll.getWidgetsAt(stack, hovered, x, y);
        return hovered.size();
    }
}

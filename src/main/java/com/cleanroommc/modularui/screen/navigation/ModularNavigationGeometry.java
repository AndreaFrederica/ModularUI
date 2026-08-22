package com.cleanroommc.modularui.screen.navigation;

import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.navigation.NavigationGeometry;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.screen.viewport.TransformationMatrix;
import com.cleanroommc.modularui.widget.sizer.Area;

/** Computes screen-space bounds without exposing ModularUI's transformation internals. */
public final class ModularNavigationGeometry {

    private ModularNavigationGeometry() {}

    public static NavigationGeometry locate(ModularScreen screen, IWidget widget) {
        Bounds bounds = rawBounds(widget);
        int screenWidth = screen.getScreenWrapper().getGuiScreen().width;
        int screenHeight = screen.getScreenWrapper().getGuiScreen().height;
        int visibleLeft = Math.max(0, bounds.left);
        int visibleTop = Math.max(0, bounds.top);
        int visibleRight = Math.min(screenWidth, bounds.right);
        int visibleBottom = Math.min(screenHeight, bounds.bottom);

        IWidget ancestor = widget;
        while (ancestor != null) {
            if (ancestor instanceof IViewport) {
                Bounds clip = rawBounds(ancestor);
                visibleLeft = Math.max(visibleLeft, clip.left);
                visibleTop = Math.max(visibleTop, clip.top);
                visibleRight = Math.min(visibleRight, clip.right);
                visibleBottom = Math.min(visibleBottom, clip.bottom);
            }
            if (ancestor instanceof ModularPanel) break;
            ancestor = ancestor.getParent();
        }

        boolean topPanel = widget.getPanel() == screen.getPanelManager().getTopMostPanel();
        return new NavigationGeometry(bounds.left, bounds.top, bounds.right, bounds.bottom,
                visibleLeft, visibleTop, Math.max(visibleLeft, visibleRight),
                Math.max(visibleTop, visibleBottom), topPanel);
    }

    private static Bounds rawBounds(IWidget widget) {
        LocatedWidget located = LocatedWidget.of(widget);
        TransformationMatrix matrix = located.getTransformationMatrix();
        Area area = widget.getArea();
        int x0 = matrix.transformX(0, 0);
        int y0 = matrix.transformY(0, 0);
        int x1 = matrix.transformX(area.w(), 0);
        int y1 = matrix.transformY(area.w(), 0);
        int x2 = matrix.transformX(0, area.h());
        int y2 = matrix.transformY(0, area.h());
        int x3 = matrix.transformX(area.w(), area.h());
        int y3 = matrix.transformY(area.w(), area.h());
        return new Bounds(Math.min(Math.min(x0, x1), Math.min(x2, x3)),
                Math.min(Math.min(y0, y1), Math.min(y2, y3)),
                Math.max(Math.max(x0, x1), Math.max(x2, x3)),
                Math.max(Math.max(y0, y1), Math.max(y2, y3)));
    }

    private static final class Bounds {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}

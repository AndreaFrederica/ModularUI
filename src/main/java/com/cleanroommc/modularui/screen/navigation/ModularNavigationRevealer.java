package com.cleanroommc.modularui.screen.navigation;

import com.cleanroommc.modularui.api.navigation.NavigationGeometry;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.AbstractScrollWidget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.ScrollData;

import java.util.ArrayList;
import java.util.List;

/** Reveals a widget through nested scroll viewports using minimum movement. */
public final class ModularNavigationRevealer {

    private ModularNavigationRevealer() {}

    public static boolean reveal(ModularScreen screen, IWidget target) {
        if (target == null || !target.isValid() || target.getScreen() != screen) return false;
        List<AbstractScrollWidget<?, ?>> scrollParents = new ArrayList<>();
        IWidget parent = target.getParent();
        while (parent != null && !(parent instanceof ModularPanel)) {
            if (parent instanceof AbstractScrollWidget<?, ?>) {
                scrollParents.add((AbstractScrollWidget<?, ?>) parent);
            }
            parent = parent.getParent();
        }

        boolean changed = false;
        for (AbstractScrollWidget<?, ?> scroll : scrollParents) {
            NavigationGeometry targetGeometry = ModularNavigationGeometry.locate(screen, target);
            NavigationGeometry viewportGeometry = ModularNavigationGeometry.locate(screen, scroll);
            ScrollArea area = scroll.getScrollArea();
            ScrollData x = area.getScrollX();
            ScrollData y = area.getScrollY();
            if (x != null) {
                int delta = targetGeometry.getLeft() < viewportGeometry.getLeft()
                        ? targetGeometry.getLeft() - viewportGeometry.getLeft()
                        : targetGeometry.getRight() > viewportGeometry.getRight()
                        ? targetGeometry.getRight() - viewportGeometry.getRight() : 0;
                int before = x.getScroll();
                if (delta != 0) x.scrollBy(area, delta);
                changed |= before != x.getScroll();
            }
            if (y != null) {
                int delta = targetGeometry.getTop() < viewportGeometry.getTop()
                        ? targetGeometry.getTop() - viewportGeometry.getTop()
                        : targetGeometry.getBottom() > viewportGeometry.getBottom()
                        ? targetGeometry.getBottom() - viewportGeometry.getBottom() : 0;
                int before = y.getScroll();
                if (delta != 0) y.scrollBy(area, delta);
                changed |= before != y.getScroll();
            }
        }
        if (changed) screen.getPanelManager().markNavigationGeometryDirty();
        return changed;
    }
}

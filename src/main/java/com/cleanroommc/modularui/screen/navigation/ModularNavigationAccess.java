package com.cleanroommc.modularui.screen.navigation;

import com.cleanroommc.modularui.api.navigation.NavigationGeometry;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.navigation.NavigationRole;
import com.cleanroommc.modularui.api.navigation.NavigationTreeEntry;
import com.cleanroommc.modularui.api.navigation.NavigationTreeView;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stable public entry point for capturing and interacting with a ModularUI navigation tree. */
public final class ModularNavigationAccess {

    private ModularNavigationAccess() {}

    public static NavigationTreeView capture(ModularScreen screen) {
        Objects.requireNonNull(screen, "screen");
        return screen.getPanelManager().doSafe(() -> captureUnsafe(screen));
    }

    public static com.cleanroommc.modularui.api.navigation.NavigationActionResult perform(
            ModularScreen screen, String path,
            com.cleanroommc.modularui.api.navigation.NavigationAction action) {
        NavigationTreeEntry entry = capture(screen).getEntry(path);
        if (entry == null) return com.cleanroommc.modularui.api.navigation.NavigationActionResult.STALE;
        return ModularNavigationDispatcher.perform(screen, entry.getWidget(), action);
    }

    public static boolean reveal(ModularScreen screen, IWidget target) {
        return ModularNavigationRevealer.reveal(screen, target);
    }

    private static NavigationTreeView captureUnsafe(ModularScreen screen) {
        List<String> roots = new ArrayList<>();
        List<NavigationTreeEntry> entries = new ArrayList<>();
        List<ModularPanel> openPanels = screen.getPanelManager().getOpenPanels();
        for (ModularPanel panel : openPanels) {
            String rootPath = "panel/" + escape(panel.getName());
            roots.add(rootPath);
            captureWidget(screen, panel, rootPath, null, entries);
        }
        ModularPanel activePanel = openPanels.isEmpty() ? null : openPanels.get(0);
        return new NavigationTreeView(screen.getPanelManager().getNavigationStructureRevision(),
                screen.getPanelManager().getNavigationGeometryRevision(), roots, entries,
                activePanel == null ? null : "panel/" + escape(activePanel.getName()));
    }

    private static void captureWidget(ModularScreen screen, IWidget widget, String path,
                                      String parentPath, List<NavigationTreeEntry> entries) {
        NavigationInfo info = widget.getNavigationInfo();
        if (widget instanceof ModularPanel && info.getRole() == NavigationRole.NONE) {
            info = NavigationInfo.builder(NavigationRole.PANEL).id(((ModularPanel) widget).getName())
                    .focusable(false).build();
        }

        List<String> childPaths = new ArrayList<>();
        Map<String, Integer> segmentCounts = new HashMap<>();
        List<IWidget> children = widget.getChildren();
        for (int i = 0; i < children.size(); i++) {
            IWidget child = children.get(i);
            String base = segment(child, i);
            int duplicate = segmentCounts.containsKey(base) ? segmentCounts.get(base) : 0;
            segmentCounts.put(base, duplicate + 1);
            String childPath = path + "/" + base + (duplicate == 0 ? "" : "~" + duplicate);
            childPaths.add(childPath);
            captureWidget(screen, child, childPath, path, entries);
        }

        NavigationGeometry geometry = ModularNavigationGeometry.locate(screen, widget);
        entries.add(new NavigationTreeEntry(path, parentPath, childPaths, widget, info, geometry,
                widget.isEnabled() && widget.areAncestorsEnabled()));
    }

    private static String segment(IWidget widget, int index) {
        NavigationInfo info = widget.getNavigationInfo();
        if (info.getId() != null && !info.getId().isEmpty()) return "id/" + escape(info.getId());
        if (widget.getName() != null && !widget.getName().isEmpty()) return "name/" + escape(widget.getName());
        return "type/" + escape(widget.getClass().getSimpleName()) + "[" + index + "]";
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace("/", "%2F").replace("~", "%7E");
    }

}

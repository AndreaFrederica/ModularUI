package com.cleanroommc.modularui.screen.navigation;

import com.cleanroommc.modularui.api.navigation.INavigationActionHandler;
import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.navigation.NavigationActionResult;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.widget.IFocusedWidget;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;

import java.util.Objects;

/** Executes a semantic action against an exact widget through ModularUI's interaction lifecycle. */
public final class ModularNavigationDispatcher {

    private ModularNavigationDispatcher() {}

    public static NavigationActionResult perform(ModularScreen screen, IWidget target, NavigationAction action) {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        NavigationActionResult result = screen.getPanelManager().doSafe(() -> performUnsafe(screen, target, action));
        return result == null ? NavigationActionResult.STALE : result;
    }

    private static NavigationActionResult performUnsafe(ModularScreen screen, IWidget target,
                                                        NavigationAction action) {
        if (!target.isValid() || target.getScreen() != screen) return NavigationActionResult.STALE;
        if (!target.isEnabled() || !target.areAncestorsEnabled()) return NavigationActionResult.REJECTED;
        if (target.getPanel() != screen.getPanelManager().getTopMostPanel()) return NavigationActionResult.REJECTED;
        NavigationInfo info = target.getNavigationInfo();
        if (!info.getActions().contains(action)) return NavigationActionResult.REJECTED;

        if (target instanceof INavigationActionHandler) {
            NavigationActionResult semantic = ((INavigationActionHandler) target).onNavigationAction(action);
            if (semantic != NavigationActionResult.IGNORED) {
                if (semantic.isChanged()) screen.getPanelManager().markNavigationStructureDirty();
                return semantic;
            }
        }

        if (action == NavigationAction.BEGIN_EDIT && target instanceof IFocusedWidget) {
            screen.getContext().focus((IFocusedWidget) target);
            return NavigationActionResult.HANDLED;
        }
        if (action == NavigationAction.END_EDIT && target instanceof IFocusedWidget) {
            if (screen.getContext().isFocused((IFocusedWidget) target)) screen.getContext().removeFocus();
            return NavigationActionResult.HANDLED;
        }
        if ((action == NavigationAction.ACTIVATE || action == NavigationAction.SECONDARY)
                && target instanceof Interactable) {
            return click(screen, target, (Interactable) target,
                    action == NavigationAction.SECONDARY ? 1 : 0);
        }
        return NavigationActionResult.IGNORED;
    }

    private static NavigationActionResult click(ModularScreen screen, IWidget target,
                                                Interactable interactable, int button) {
        LocatedWidget located = LocatedWidget.of(target);
        located.applyMatrix(screen.getContext());
        try {
            Interactable.Result pressed = interactable.onMousePressed(button);
            boolean handled = pressed.accepts || pressed.stops;
            boolean changed = false;
            if (pressed.accepts && target.isValid()) {
                Interactable.Result tapped = interactable.onMouseTapped(button);
                handled |= tapped.accepts || tapped.stops;
                changed |= tapped.accepts;
            }
            if (target.isValid()) handled |= interactable.onMouseRelease(button);
            if (handled) {
                if (target instanceof IFocusedWidget) screen.getContext().focus((IFocusedWidget) target);
                else screen.getContext().removeFocus();
                screen.getPanelManager().markNavigationStructureDirty();
            }
            return !handled ? NavigationActionResult.IGNORED
                    : changed ? NavigationActionResult.CHANGED : NavigationActionResult.HANDLED;
        } finally {
            located.unapplyMatrix(screen.getContext());
        }
    }
}

package com.cleanroommc.modularui.screen.event;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.InputModifiers;
import com.cleanroommc.modularui.api.event.MuiEvent;
import com.cleanroommc.modularui.api.event.MuiEventPhase;
import com.cleanroommc.modularui.api.event.MuiEventType;
import com.cleanroommc.modularui.api.event.PointerEvent;
import com.cleanroommc.modularui.api.dom.LegacyWidgetElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Synchronous capture/target/bubble dispatcher with immutable event paths. */
public final class MuiEventDispatcher {

    private final ModularScreen screen;
    private final Map<Integer, WeakReference<IEventTarget>> pointerCaptures = new HashMap<>();

    public MuiEventDispatcher(ModularScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    public static MuiEventDispatcher forTarget(IEventTarget target) {
        target = EventListenerRegistry.canonicalize(target);
        if (target instanceof IWidget widget && widget.isValid()) {
            return widget.getScreen().getEventDispatcher();
        }
        if (target instanceof LegacyWidgetElement element) {
            IWidget widget = element.getWidgetInternal();
            if (widget.isValid()) return widget.getScreen().getEventDispatcher();
        }
        throw new IllegalStateException("Pointer capture requires a mounted event target");
    }

    public boolean dispatch(IEventTarget target, MuiEvent event) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(event, "event");
        target = EventListenerRegistry.canonicalize(target);
        IEventTarget canonicalTarget = target;
        Boolean result = this.screen.getPanelManager().doSafe(() -> dispatchAlongPath(canonicalTarget, event));
        return result == null || result;
    }

    public static boolean dispatchDetached(IEventTarget target, MuiEvent event) {
        return dispatchAlongPath(Objects.requireNonNull(target, "target"), Objects.requireNonNull(event, "event"));
    }

    private static boolean dispatchAlongPath(IEventTarget target, MuiEvent event) {
        List<IEventTarget> path = buildPath(target);
        event.beginDispatch(target);
        try {
            for (int i = path.size() - 1; i > 0 && !event.isPropagationStopped(); i--) {
                invoke(path.get(i), event, MuiEventPhase.CAPTURING, true);
            }
            if (!event.isPropagationStopped()) {
                invoke(target, event, MuiEventPhase.AT_TARGET, true);
                if (!event.isImmediatePropagationStopped()) {
                    invoke(target, event, MuiEventPhase.AT_TARGET, false);
                }
            }
            if (event.bubbles() && !event.isPropagationStopped()) {
                for (int i = 1; i < path.size() && !event.isPropagationStopped(); i++) {
                    invoke(path.get(i), event, MuiEventPhase.BUBBLING, false);
                }
            }
            return !event.isDefaultPrevented();
        } finally {
            event.endDispatch();
        }
    }

    private static List<IEventTarget> buildPath(IEventTarget target) {
        List<IEventTarget> path = new ArrayList<>();
        IdentityHashMap<IEventTarget, Boolean> visited = new IdentityHashMap<>();
        IEventTarget current = target;
        while (current != null) {
            if (visited.put(current, Boolean.TRUE) != null) {
                throw new IllegalStateException("Cycle in event target parent chain");
            }
            path.add(current);
            current = current.getEventParent();
        }
        return Collections.unmodifiableList(path);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(IEventTarget target, MuiEvent event, MuiEventPhase phase, boolean capture) {
        MuiEventType type = event.getType();
        List<EventListenerRegistry.Invocation<MuiEvent>> listeners = EventListenerRegistry.snapshot(target, type, capture);
        for (EventListenerRegistry.Invocation<MuiEvent> invocation : listeners) {
            if (event.isImmediatePropagationStopped()) break;
            event.beginListener(target, phase, invocation.options.isPassive());
            try {
                invocation.listener.handleEvent(event);
            } catch (RuntimeException exception) {
                ModularUI.LOGGER.error("Unhandled listener exception while dispatching '{}'", event.getType(), exception);
            } finally {
                event.endListener();
            }
        }
    }

    public void setPointerCapture(int pointerId, IEventTarget target) {
        if (pointerId < 0) throw new IllegalArgumentException("pointerId must be non-negative");
        target = EventListenerRegistry.canonicalize(target);
        validateMountedTarget(target);
        this.pointerCaptures.put(pointerId, new WeakReference<>(target));
    }

    public void releasePointerCapture(int pointerId, IEventTarget target) {
        target = EventListenerRegistry.canonicalize(target);
        IEventTarget captured = capturedTarget(pointerId);
        if (captured == target) this.pointerCaptures.remove(pointerId);
    }

    public void releasePointerCapture(int pointerId) {
        this.pointerCaptures.remove(pointerId);
    }

    public boolean hasPointerCapture(int pointerId, IEventTarget target) {
        return capturedTarget(pointerId) == EventListenerRegistry.canonicalize(target);
    }

    public @Nullable IEventTarget resolvePointerTarget(int pointerId, @Nullable IEventTarget fallback) {
        IEventTarget captured = capturedTarget(pointerId);
        return captured == null ? fallback : captured;
    }

    public void clearTarget(IEventTarget target) {
        target = EventListenerRegistry.canonicalize(target);
        List<Integer> capturedPointers = new ArrayList<>();
        for (Map.Entry<Integer, WeakReference<IEventTarget>> entry : this.pointerCaptures.entrySet()) {
            if (entry.getValue().get() == target) capturedPointers.add(entry.getKey());
        }
        for (int pointerId : capturedPointers) {
            this.pointerCaptures.remove(pointerId);
            try {
                dispatch(target, new PointerEvent(PointerEvent.CANCEL, pointerId, 0, 0,
                        -1, 0, 0, 0, 0, InputModifiers.NONE));
            } finally {
                releasePointerCapture(pointerId, target);
            }
        }
    }

    private @Nullable IEventTarget capturedTarget(int pointerId) {
        WeakReference<IEventTarget> reference = this.pointerCaptures.get(pointerId);
        if (reference == null) return null;
        IEventTarget target = reference.get();
        if (target == null || !isMountedTarget(target)) {
            this.pointerCaptures.remove(pointerId);
            return null;
        }
        return target;
    }

    private void validateMountedTarget(IEventTarget target) {
        Objects.requireNonNull(target, "target");
        if (!isMountedTarget(target)) {
            throw new IllegalStateException("Pointer capture target is not mounted in this screen");
        }
    }

    private boolean isMountedTarget(IEventTarget target) {
        if (target instanceof IWidget widget) {
            return widget.isValid() && widget.getScreen() == this.screen;
        }
        if (target instanceof MuiNode node) {
            return node.isConnected() && node.getOwnerDocument() == this.screen.getDocument();
        }
        return true;
    }
}

package com.cleanroommc.modularui.api.event;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Base class for synchronous DOM-style events. An event instance can only be dispatched once. */
public class MuiEvent {

    private final MuiEventType<? extends MuiEvent> type;
    private final long timestampNanos;
    private IEventTarget target;
    private IEventTarget currentTarget;
    private MuiEventPhase eventPhase = MuiEventPhase.NONE;
    private boolean dispatching;
    private boolean dispatched;
    private boolean passiveListener;
    private boolean defaultPrevented;
    private boolean propagationStopped;
    private boolean immediatePropagationStopped;

    public MuiEvent(MuiEventType<? extends MuiEvent> type) {
        this(type, System.nanoTime());
    }

    public MuiEvent(MuiEventType<? extends MuiEvent> type, long timestampNanos) {
        this.type = Objects.requireNonNull(type, "type");
        this.timestampNanos = timestampNanos;
    }

    public MuiEventType<? extends MuiEvent> getType() {
        return this.type;
    }

    public long getTimestampNanos() {
        return this.timestampNanos;
    }

    public boolean bubbles() {
        return this.type.bubbles();
    }

    public boolean isCancelable() {
        return this.type.isCancelable();
    }

    public @Nullable IEventTarget getTarget() {
        return this.target;
    }

    public @Nullable IEventTarget getCurrentTarget() {
        return this.currentTarget;
    }

    public MuiEventPhase getEventPhase() {
        return this.eventPhase;
    }

    public boolean isDefaultPrevented() {
        return this.defaultPrevented;
    }

    public boolean isPropagationStopped() {
        return this.propagationStopped;
    }

    public boolean isImmediatePropagationStopped() {
        return this.immediatePropagationStopped;
    }

    public void preventDefault() {
        if (isCancelable() && !this.passiveListener) this.defaultPrevented = true;
    }

    public void stopPropagation() {
        this.propagationStopped = true;
    }

    public void stopImmediatePropagation() {
        this.immediatePropagationStopped = true;
        this.propagationStopped = true;
    }

    @ApiStatus.Internal
    public void beginDispatch(IEventTarget target) {
        if (this.dispatching || this.dispatched) {
            throw new IllegalStateException("An event instance can only be dispatched once");
        }
        this.target = Objects.requireNonNull(target, "target");
        this.dispatching = true;
    }

    @ApiStatus.Internal
    public void beginListener(IEventTarget currentTarget, MuiEventPhase phase, boolean passive) {
        this.currentTarget = currentTarget;
        this.eventPhase = phase;
        this.passiveListener = passive;
    }

    @ApiStatus.Internal
    public void endListener() {
        this.passiveListener = false;
    }

    @ApiStatus.Internal
    public void endDispatch() {
        this.currentTarget = null;
        this.eventPhase = MuiEventPhase.NONE;
        this.passiveListener = false;
        this.dispatching = false;
        this.dispatched = true;
    }
}

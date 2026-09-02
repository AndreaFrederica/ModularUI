package com.cleanroommc.modularui.api.event;

import org.jetbrains.annotations.Nullable;

public final class FocusEvent extends MuiEvent {

    public static final MuiEventType<FocusEvent> FOCUS = new MuiEventType<>("focus", false, false);
    public static final MuiEventType<FocusEvent> BLUR = new MuiEventType<>("blur", false, false);
    public static final MuiEventType<FocusEvent> FOCUS_IN = new MuiEventType<>("focusin", true, false);
    public static final MuiEventType<FocusEvent> FOCUS_OUT = new MuiEventType<>("focusout", true, false);

    private final IEventTarget relatedTarget;

    public FocusEvent(MuiEventType<FocusEvent> type, @Nullable IEventTarget relatedTarget) {
        super(type);
        this.relatedTarget = relatedTarget;
    }

    public @Nullable IEventTarget getRelatedTarget() {
        return this.relatedTarget;
    }
}

package com.cleanroommc.modularui.api.event;

import java.util.Objects;

public final class PointerEvent extends MuiEvent {

    public static final MuiEventType<PointerEvent> DOWN = new MuiEventType<>("pointerdown", true, true);
    public static final MuiEventType<PointerEvent> UP = new MuiEventType<>("pointerup", true, true);
    public static final MuiEventType<PointerEvent> MOVE = new MuiEventType<>("pointermove", true, true);
    public static final MuiEventType<PointerEvent> CANCEL = new MuiEventType<>("pointercancel", true, false);
    public static final MuiEventType<PointerEvent> WHEEL = new MuiEventType<>("wheel", true, true);

    private final int pointerId;
    private final int screenX;
    private final int screenY;
    private final int button;
    private final int buttons;
    private final int deltaX;
    private final int deltaY;
    private final long durationMillis;
    private final InputModifiers modifiers;

    public PointerEvent(MuiEventType<PointerEvent> type, int pointerId, int screenX, int screenY,
                        int button, int buttons, int deltaX, int deltaY, long durationMillis,
                        InputModifiers modifiers) {
        super(type);
        this.pointerId = pointerId;
        this.screenX = screenX;
        this.screenY = screenY;
        this.button = button;
        this.buttons = buttons;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.durationMillis = durationMillis;
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
    }

    public int getPointerId() { return this.pointerId; }
    public int getScreenX() { return this.screenX; }
    public int getScreenY() { return this.screenY; }
    public int getButton() { return this.button; }
    public int getButtons() { return this.buttons; }
    public int getDeltaX() { return this.deltaX; }
    public int getDeltaY() { return this.deltaY; }
    public long getDurationMillis() { return this.durationMillis; }
    public InputModifiers getModifiers() { return this.modifiers; }
}

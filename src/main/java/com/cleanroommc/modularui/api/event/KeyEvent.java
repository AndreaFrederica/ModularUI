package com.cleanroommc.modularui.api.event;

import java.util.Objects;

public final class KeyEvent extends MuiEvent {

    public static final MuiEventType<KeyEvent> DOWN = new MuiEventType<>("keydown", true, true);
    public static final MuiEventType<KeyEvent> UP = new MuiEventType<>("keyup", true, true);

    private final char typedChar;
    private final int keyCode;
    private final boolean repeat;
    private final InputModifiers modifiers;

    public KeyEvent(MuiEventType<KeyEvent> type, char typedChar, int keyCode, boolean repeat,
                    InputModifiers modifiers) {
        super(type);
        this.typedChar = typedChar;
        this.keyCode = keyCode;
        this.repeat = repeat;
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
    }

    public char getTypedChar() { return this.typedChar; }
    public int getKeyCode() { return this.keyCode; }
    public boolean isRepeat() { return this.repeat; }
    public InputModifiers getModifiers() { return this.modifiers; }
}

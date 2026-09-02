package com.cleanroommc.modularui.api.event;

public final class InputModifiers {

    public static final int SHIFT = 1;
    public static final int CONTROL = 1 << 1;
    public static final int ALT = 1 << 2;
    public static final int META = 1 << 3;
    public static final InputModifiers NONE = new InputModifiers(0);

    private final int mask;

    public InputModifiers(int mask) {
        this.mask = mask & (SHIFT | CONTROL | ALT | META);
    }

    public static InputModifiers of(boolean shift, boolean control, boolean alt, boolean meta) {
        int mask = (shift ? SHIFT : 0) | (control ? CONTROL : 0) | (alt ? ALT : 0) | (meta ? META : 0);
        return mask == 0 ? NONE : new InputModifiers(mask);
    }

    public int getMask() {
        return this.mask;
    }

    public boolean isShiftDown() {
        return (this.mask & SHIFT) != 0;
    }

    public boolean isControlDown() {
        return (this.mask & CONTROL) != 0;
    }

    public boolean isAltDown() {
        return (this.mask & ALT) != 0;
    }

    public boolean isMetaDown() {
        return (this.mask & META) != 0;
    }
}

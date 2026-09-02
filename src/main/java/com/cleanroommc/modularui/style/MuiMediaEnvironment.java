package com.cleanroommc.modularui.style;

/** Immutable viewport facts used when evaluating a CSS media query. */
public final class MuiMediaEnvironment {
    public static final MuiMediaEnvironment UNCONSTRAINED = new MuiMediaEnvironment(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int width;
    private final int height;

    public MuiMediaEnvironment(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

package com.cleanroommc.modularui.api.text;

import org.jetbrains.annotations.Nullable;

/** Registry for optional text integrations such as NeoFontRender. */
public final class MuiTextBackends {

    private static volatile MuiTextBackend backend;

    private MuiTextBackends() {}

    public static void register(@Nullable MuiTextBackend value) {
        backend = value;
    }

    @Nullable
    public static MuiTextBackend get() {
        return backend;
    }

    public static boolean isAvailable() {
        MuiTextBackend value = backend;
        try {
            return value != null && value.isAvailable();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Returns NaN when no optional backend is active. */
    public static float measure(String text, float scale, int color, boolean shadow) {
        MuiTextBackend value = backend;
        if (value == null || text == null || text.isEmpty()) return Float.NaN;
        try {
            if (!value.isAvailable()) return Float.NaN;
            float measured = value.measure(text, scale, color, shadow);
            return Float.isFinite(measured) && measured >= 0 ? measured : Float.NaN;
        } catch (RuntimeException | LinkageError ignored) {
            return Float.NaN;
        }
    }
}

package com.cleanroommc.modularui.api.text;

/**
 * Optional client-side text measurement bridge for font mods.
 *
 * <p>Implementations must return a width in MUI screen units, including the
 * supplied scale. MUI never requires an implementation and falls back to the
 * vanilla FontRenderer when none is registered.</p>
 */
public interface MuiTextBackend {

    /** @return true when the backend can measure the current render state. */
    boolean isAvailable();

    /**
     * Measures a formatted string using the same backend that draws it.
     *
     * @param text  Minecraft-formatted text
     * @param scale MUI text scale
     * @param color ARGB color used for rendering
     * @param shadow whether the shadow pass is requested
     */
    float measure(String text, float scale, int color, boolean shadow);
}

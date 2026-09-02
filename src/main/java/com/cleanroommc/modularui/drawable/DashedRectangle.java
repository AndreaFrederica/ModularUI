package com.cleanroommc.modularui.drawable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Color;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Lightweight rectangular dashed outline used by the CSS border subset. */
public final class DashedRectangle implements IDrawable {
    private final int color;
    private final int thickness;
    private final int dash;
    private final int gap;

    public DashedRectangle(int color, int thickness, int dash, int gap) {
        this.color = color;
        this.thickness = Math.max(1, thickness);
        this.dash = Math.max(1, dash);
        this.gap = Math.max(0, gap);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        Color.setGlColorOpaque(Color.WHITE.main);
        drawHorizontal(x, y, width);
        drawHorizontal(x, y + Math.max(0, height - thickness), width);
        drawVertical(x, y + thickness, Math.max(0, height - thickness * 2));
        drawVertical(x + Math.max(0, width - thickness), y + thickness, Math.max(0, height - thickness * 2));
    }

    private void drawHorizontal(int x, int y, int length) {
        for (int offset = 0; offset < length; offset += dash + gap) {
            GuiDraw.drawRect(x + offset, y, Math.min(dash, length - offset), thickness, color);
        }
    }

    private void drawVertical(int x, int y, int length) {
        for (int offset = 0; offset < length; offset += dash + gap) {
            GuiDraw.drawRect(x, y + offset, thickness, Math.min(dash, length - offset), color);
        }
    }

    @Override
    public boolean canApplyTheme() {
        return false;
    }
}

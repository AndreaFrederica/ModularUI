package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.drawable.DrawableSerialization;
import com.cleanroommc.modularui.drawable.DashedRectangle;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.AbstractScrollWidget;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.widget.sizer.Box;
import com.cleanroommc.modularui.theme.WidgetThemeKey;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Applies the small, Widget-backed subset of a computed style. The cascade remains
 * independent from legacy Widgets, so unsupported declarations can still be exposed
 * to a future layout or renderer adapter.
 */
public final class MuiStyleApplier {

    private final Map<Widget<?>, Baseline> baselines = new IdentityHashMap<>();
    private final Map<MuiElement, MuiComputedStyle> computed = new IdentityHashMap<>();

    public MuiComputedStyle apply(MuiElement element, Widget<?> widget, MuiComputedStyle style) {
        Baseline baseline = this.baselines.get(widget);
        if (baseline == null) {
            baseline = Baseline.capture(widget);
            this.baselines.put(widget, baseline);
        }
        // Rebuild the Widget-backed projection from the original values on every
        // recomputation. This makes removing an inline/rule declaration deterministic.
        baseline.restore(widget);
        this.computed.put(element, style);

        JsonElement enabled = style.get("enabled");
        JsonElement visibility = style.get("visibility");
        if (enabled != null || visibility != null) {
            boolean isEnabled = enabled == null || asBoolean(enabled, true);
            boolean visible = visibility == null || !"hidden".equalsIgnoreCase(asString(visibility, "visible"));
            widget.setEnabled(isEnabled && visible);
        }

        if (widget instanceof Widget<?>) {
            applyNavigation(widget, style);
            applyGeometry(widget, style);
            applyBox(widget, style);
            applyBackground(widget, style);
            applyBorder(widget, style);
            applyTheme(widget, style);
            applyLayout(widget, style);
            applyOverflow(widget, style);
            applyScrollbar(widget, style);
        }
        if (widget instanceof TextWidget<?>) applyText((TextWidget<?>) widget, style);
        if (widget instanceof ProgressWidget) applyProgress((ProgressWidget) widget, style);
        return style;
    }

    public @Nullable MuiComputedStyle getComputedStyle(MuiElement element) {
        return this.computed.get(element);
    }

    public void remove(MuiElement element, Widget<?> widget) {
        this.computed.remove(element);
        Baseline baseline = this.baselines.remove(widget);
        if (baseline != null) baseline.restore(widget);
    }

    public void clear() {
        for (Map.Entry<Widget<?>, Baseline> entry : this.baselines.entrySet()) {
            entry.getValue().restore(entry.getKey());
        }
        this.computed.clear();
        this.baselines.clear();
    }

    private static void applyGeometry(Widget<?> widget, MuiComputedStyle style) {
        applyLength(style.get("left"), widget, Length.LEFT);
        applyLength(style.get("top"), widget, Length.TOP);
        applyLength(style.get("right"), widget, Length.RIGHT);
        applyLength(style.get("bottom"), widget, Length.BOTTOM);
        applyLength(style.get("width"), widget, Length.WIDTH);
        applyLength(style.get("height"), widget, Length.HEIGHT);
    }

    private static void applyNavigation(Widget<?> widget, MuiComputedStyle style) {
        if (!(widget instanceof AbstractWidget)) return;
        AbstractWidget abstractWidget = (AbstractWidget) widget;
        NavigationInfo navigation = abstractWidget.getNavigationInfo();
        JsonElement focusable = style.get("focusable");
        if (focusable != null) navigation = navigation.withFocusable(asBoolean(focusable, navigation.isFocusable()));
        JsonElement tabIndex = style.get("tab-index");
        if (tabIndex != null) {
            try {
                navigation = navigation.withOrder(Integer.parseInt(tabIndex.getAsString().trim()));
            } catch (RuntimeException ignored) { }
        }
        if (focusable != null || tabIndex != null) abstractWidget.setNavigationInfo(navigation);
    }

    private static void applyLength(@Nullable JsonElement value, Widget<?> widget, Length target) {
        if (value == null || !value.isJsonPrimitive()) return;
        String raw = value.getAsString().trim();
        try {
            if (raw.endsWith("%")) {
                float relative = Float.parseFloat(raw.substring(0, raw.length() - 1).trim()) / 100f;
                switch (target) {
                    case LEFT: widget.leftRel(relative); return;
                    case TOP: widget.topRel(relative); return;
                    case RIGHT: widget.rightRel(relative); return;
                    case BOTTOM: widget.bottomRel(relative); return;
                    case WIDTH: widget.widthRel(relative); return;
                    case HEIGHT: widget.heightRel(relative); return;
                }
            }
            int pixels = Integer.parseInt(stripUnit(raw));
            switch (target) {
                case LEFT: widget.left(pixels); break;
                case TOP: widget.top(pixels); break;
                case RIGHT: widget.right(pixels); break;
                case BOTTOM: widget.bottom(pixels); break;
                case WIDTH: widget.width(pixels); break;
                case HEIGHT: widget.height(pixels); break;
            }
        } catch (RuntimeException ignored) { }
    }

    private enum Length { LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT }

    private static void applyBox(Widget<?> widget, MuiComputedStyle style) {
        if (style.has("padding")) {
            int[] values = box(style.get("padding"));
            widget.padding(values[3], values[1], values[0], values[2]);
        }
        if (style.has("margin")) {
            int[] values = box(style.get("margin"));
            widget.margin(values[3], values[1], values[0], values[2]);
        }
    }

    private static void applyBackground(Widget<?> widget, MuiComputedStyle style) {
        JsonElement value = style.get("background");
        if (value == null) return;
        if (value.isJsonPrimitive() && "transparent".equalsIgnoreCase(value.getAsString().trim())) {
            widget.background(IDrawable.NONE);
            return;
        }
        try {
            IDrawable drawable;
            if (value.isJsonPrimitive()) drawable = new Rectangle().color(Color.ofJson(value));
            else drawable = DrawableSerialization.deserialize(value);
            if (drawable != null) widget.background(drawable);
        } catch (RuntimeException ignored) {
            // Invalid optional style declarations must not make a legacy Widget unusable.
        }
    }

    private static void applyBorder(Widget<?> widget, MuiComputedStyle style) {
        String borderStyle = style.getString("border-style");
        if (!"dashed".equalsIgnoreCase(borderStyle) && !"solid".equalsIgnoreCase(borderStyle)) return;
        int color = 0xff596777;
        JsonElement colorValue = style.get("border-color");
        if (colorValue != null) color = color(colorValue, color);
        int thickness = Math.max(1, asLengthInt(style.get("border-width"), 1));
        if ("solid".equalsIgnoreCase(borderStyle)) widget.overlay(new Rectangle().color(color).hollow(thickness));
        else {
            int dash = Math.max(1, asLengthInt(style.get("border-dash"), 3));
            int gap = Math.max(0, asLengthInt(style.get("border-gap"), 2));
            widget.overlay(new DashedRectangle(color, thickness, dash, gap));
        }
    }

    private static void applyProgress(ProgressWidget widget, MuiComputedStyle style) {
        if (!"bar".equalsIgnoreCase(style.getString("progress-render"))) {
            widget.clearCssBar();
            return;
        }
        widget.cssBar(color(style.get("progress-track-color"), 0xff202832),
                color(style.get("progress-fill-color"), 0xff38bda6));
        String direction = style.getString("progress-direction");
        if (direction != null) {
            try {
                widget.direction(ProgressWidget.Direction.valueOf(direction.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private static int color(JsonElement value, int fallback) {
        if (value == null) return fallback;
        if (value.isJsonPrimitive() && "transparent".equalsIgnoreCase(value.getAsString().trim())) return 0;
        try { return Color.ofJson(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static void applyLayout(Widget<?> widget, MuiComputedStyle style) {
        if (widget instanceof Flow) {
            Flow flow = (Flow) widget;
            if (style.has("gap")) {
                int gap = asLengthInt(style.get("gap"), 0);
                flow.childPadding(gap).crossAxisChildPadding(gap);
            }
            if (style.has("wrap")) flow.wrap(asBoolean(style.get("wrap"), flow.isWrap()));
            String justify = style.getString("justify");
            if (justify != null) {
                try { flow.mainAxisAlignment(FlowAlignment.main(justify)); }
                catch (IllegalArgumentException ignored) { }
            }
            String align = style.getString("align");
            if (align != null) {
                try { flow.crossAxisAlignment(FlowAlignment.cross(align)); }
                catch (IllegalArgumentException ignored) { }
            }
        } else if (widget instanceof Grid) {
            Grid grid = (Grid) widget;
            JsonElement columns = style.get("grid-columns");
            if (columns == null) columns = style.get("columns");
            if (columns != null) {
                try { grid.setDomColumns(Math.max(1, asLengthInt(columns, grid.getDomColumns()))); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    /**
     * Maps the supported overflow subset to the existing ScrollArea. The
     * adapter deliberately handles only AbstractScrollWidget instances; a
     * normal Widget has no viewport semantics to alter.
     */
    private static void applyOverflow(Widget<?> widget, MuiComputedStyle style) {
        if (!(widget instanceof AbstractScrollWidget)) return;
        AbstractScrollWidget<?, ?> scrollWidget = (AbstractScrollWidget<?, ?>) widget;
        JsonElement shorthand = style.get("overflow");
        JsonElement x = style.get("overflow-x");
        JsonElement y = style.get("overflow-y");
        if (x == null) x = shorthand;
        if (y == null) y = shorthand;
        applyOverflowAxis(scrollWidget, GuiAxis.X, x);
        applyOverflowAxis(scrollWidget, GuiAxis.Y, y);
    }

    private static void applyOverflowAxis(AbstractScrollWidget<?, ?> widget, GuiAxis axis, @Nullable JsonElement value) {
        if (value == null) return;
        String mode = asString(value, "").trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(mode) || "scroll".equals(mode)) {
            if (widget.getScrollArea().getScrollData(axis) == null) {
                if (axis.isHorizontal()) widget.getScrollArea().setScrollDataX(new HorizontalScrollData());
                else widget.getScrollArea().setScrollDataY(new VerticalScrollData());
            }
        } else if ("hidden".equals(mode) || "visible".equals(mode)) {
            if (widget.isScrollAxisRequired(axis)) return;
            if (axis.isHorizontal()) widget.getScrollArea().removeScrollDataX();
            else widget.getScrollArea().removeScrollDataY();
        }
    }

    private static void applyScrollbar(Widget<?> widget, MuiComputedStyle style) {
        if (!(widget instanceof AbstractScrollWidget)) return;
        AbstractScrollWidget<?, ?> scrollWidget = (AbstractScrollWidget<?, ?>) widget;
        JsonElement background = style.get("scrollbar-background");
        if (background != null) {
            try { scrollWidget.getScrollArea().setScrollBarBackgroundColor(Color.ofJson(background)); }
            catch (RuntimeException ignored) { }
        }
        JsonElement texture = style.get("scrollbar");
        if (texture != null && !texture.isJsonNull()) {
            try {
                IDrawable drawable = DrawableSerialization.deserialize(texture);
                if (drawable != null) {
                    if (scrollWidget.getScrollArea().getScrollX() != null) scrollWidget.getScrollArea().getScrollX().texture(drawable);
                    if (scrollWidget.getScrollArea().getScrollY() != null) scrollWidget.getScrollArea().getScrollY().texture(drawable);
                }
            } catch (RuntimeException ignored) { }
        }
        JsonElement speed = style.get("scrollbar-speed");
        if (speed != null) {
            int value = asLengthInt(speed, -1);
            if (value >= 0) {
                if (scrollWidget.getScrollArea().getScrollX() != null) scrollWidget.getScrollArea().getScrollX().setScrollSpeed(value);
                if (scrollWidget.getScrollArea().getScrollY() != null) scrollWidget.getScrollArea().getScrollY().setScrollSpeed(value);
            }
        }
        JsonElement cancel = style.get("scrollbar-cancel-edge");
        if (cancel != null) {
            boolean value = asBoolean(cancel, true);
            if (scrollWidget.getScrollArea().getScrollX() != null) scrollWidget.getScrollArea().getScrollX().setCancelScrollEdge(value);
            if (scrollWidget.getScrollArea().getScrollY() != null) scrollWidget.getScrollArea().getScrollY().setCancelScrollEdge(value);
        }
    }

    private static void applyTheme(Widget<?> widget, MuiComputedStyle style) {
        String key = style.getString("theme-key");
        if (key != null && !key.trim().isEmpty()) {
            try { widget.widgetTheme(key.trim()); }
            catch (RuntimeException ignored) { }
        }
    }

    private static void applyText(TextWidget<?> widget, MuiComputedStyle style) {
        if (style.has("color")) {
            try { widget.color(Color.ofJson(style.get("color"))); }
            catch (RuntimeException ignored) { }
        }
        if (style.has("font-size")) {
            float size = asFloat(style.get("font-size"), widget.getScale());
            if (size > 0f) widget.scale(size);
        }
        String align = style.getString("text-align");
        if (align != null) {
            try { widget.textAlign(new Alignment.Json().deserialize(new com.google.gson.JsonPrimitive(align), Alignment.class, null)); }
            catch (RuntimeException ignored) { }
        }
    }

    private static int[] box(JsonElement value) {
        String text = asString(value, "0").trim();
        String[] parts = text.split("\\s+");
        int[] parsed = new int[Math.min(parts.length, 4)];
        for (int i = 0; i < parsed.length; i++) parsed[i] = Integer.parseInt(stripUnit(parts[i]));
        if (parsed.length == 0) return new int[] {0, 0, 0, 0};
        if (parsed.length == 1) return new int[] {parsed[0], parsed[0], parsed[0], parsed[0]};
        if (parsed.length == 2) return new int[] {parsed[0], parsed[1], parsed[0], parsed[1]};
        if (parsed.length == 3) return new int[] {parsed[0], parsed[1], parsed[2], parsed[1]};
        return new int[] {parsed[0], parsed[1], parsed[2], parsed[3]};
    }

    private static int asLengthInt(JsonElement value, int fallback) {
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try { return Integer.parseInt(stripUnit(value.getAsString())); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static final class FlowAlignment {
        static Alignment.MainAxis main(String value) {
            return Alignment.MainAxis.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
        static Alignment.CrossAxis cross(String value) {
            return Alignment.CrossAxis.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    private static String asString(JsonElement value, String fallback) {
        return value == null || !value.isJsonPrimitive() ? fallback : value.getAsString();
    }

    private static boolean asBoolean(JsonElement value, boolean fallback) {
        try { return value == null ? fallback : value.getAsBoolean(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String stripUnit(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("px")) return normalized.substring(0, normalized.length() - 2).trim();
        return normalized;
    }

    private static float asFloat(JsonElement value, float fallback) {
        try { return value == null ? fallback : value.getAsFloat(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static final class Baseline {
        private final boolean enabled;
        private final IDrawable background;
        private final IDrawable overlay;
        private final boolean disableThemeBackground;
        private final WidgetThemeKey<?> themeKey;
        private final Box padding;
        private final Box margin;
        private final Integer textColor;
        private final float textScale;
        private final Alignment textAlignment;
        private final Alignment.MainAxis flowMain;
        private final Alignment.CrossAxis flowCross;
        private final int flowGap;
        private final int flowCrossGap;
        private final boolean flowWrap;
        private final int gridColumns;
        private final int scrollBarBackgroundColor;
        @Nullable private final HorizontalScrollData scrollX;
        @Nullable private final VerticalScrollData scrollY;
        private final int scrollXSpeed;
        private final int scrollYSpeed;
        private final boolean scrollXCancelEdge;
        private final boolean scrollYCancelEdge;
        @Nullable private final IDrawable scrollXTexture;
        @Nullable private final IDrawable scrollYTexture;
        @Nullable private final NavigationInfo navigationOverride;
        private final boolean cssBar;
        private final int cssTrackColor;
        private final int cssFillColor;
        private final ProgressWidget.Direction progressDirection;

        private Baseline(Widget<?> widget) {
            this.enabled = widget.isEnabled();
            this.background = widget.getBackground();
            this.overlay = widget.getOverlay();
            this.disableThemeBackground = widget.isDisableThemeBackground();
            this.themeKey = widget.getWidgetThemeOverride();
            this.padding = widget.getArea().getPadding().copyOrImmutable();
            this.margin = widget.getArea().getMargin().copyOrImmutable();
            if (widget instanceof TextWidget<?>) {
                TextWidget<?> text = (TextWidget<?>) widget;
                this.textColor = text.getColor() == null ? null : text.getColor().getAsInt();
                this.textScale = text.getScale();
                this.textAlignment = text.getAlignment();
            } else {
                this.textColor = null;
                this.textScale = 1f;
                this.textAlignment = null;
            }
            if (widget instanceof Flow) {
                Flow flow = (Flow) widget;
                this.flowMain = flow.getMaa();
                this.flowCross = flow.getCaa();
                this.flowGap = flow.getChildPadding();
                this.flowCrossGap = flow.getCrossAxisChildPadding();
                this.flowWrap = flow.isWrap();
            } else {
                this.flowMain = null;
                this.flowCross = null;
                this.flowGap = 0;
                this.flowCrossGap = 0;
                this.flowWrap = false;
            }
            this.gridColumns = widget instanceof Grid ? ((Grid) widget).getDomColumns() : 1;
            if (widget instanceof AbstractScrollWidget) {
                AbstractScrollWidget<?, ?> scrollWidget = (AbstractScrollWidget<?, ?>) widget;
                this.scrollBarBackgroundColor = scrollWidget.getScrollArea().getScrollBarBackgroundColor();
                this.scrollX = scrollWidget.getScrollArea().getScrollX();
                this.scrollY = scrollWidget.getScrollArea().getScrollY();
                this.scrollXSpeed = this.scrollX == null ? 0 : this.scrollX.getScrollSpeed();
                this.scrollYSpeed = this.scrollY == null ? 0 : this.scrollY.getScrollSpeed();
                this.scrollXCancelEdge = this.scrollX == null || this.scrollX.isCancelScrollEdge();
                this.scrollYCancelEdge = this.scrollY == null || this.scrollY.isCancelScrollEdge();
                this.scrollXTexture = this.scrollX == null ? null : this.scrollX.getScrollbarTexture();
                this.scrollYTexture = this.scrollY == null ? null : this.scrollY.getScrollbarTexture();
            } else {
                this.scrollBarBackgroundColor = 0;
                this.scrollX = null;
                this.scrollY = null;
                this.scrollXSpeed = 0;
                this.scrollYSpeed = 0;
                this.scrollXCancelEdge = true;
                this.scrollYCancelEdge = true;
                this.scrollXTexture = null;
                this.scrollYTexture = null;
            }
            this.navigationOverride = widget instanceof AbstractWidget
                    ? ((AbstractWidget) widget).getNavigationInfoOverride() : null;
            if (widget instanceof ProgressWidget) {
                ProgressWidget progress = (ProgressWidget) widget;
                this.cssBar = progress.isCssBar();
                this.cssTrackColor = progress.getCssTrackColor();
                this.cssFillColor = progress.getCssFillColor();
                this.progressDirection = progress.getDirection();
            } else {
                this.cssBar = false;
                this.cssTrackColor = 0xff202832;
                this.cssFillColor = 0xff38bda6;
                this.progressDirection = ProgressWidget.Direction.RIGHT;
            }
        }

        static Baseline capture(Widget<?> widget) { return new Baseline(widget); }

        void restore(Widget<?> widget) {
            widget.setEnabled(this.enabled);
            if (widget instanceof AbstractWidget) {
                ((AbstractWidget) widget).setNavigationInfo(this.navigationOverride);
            }
            if (this.background == null) widget.backgroundOverlay(IDrawable.NONE);
            else widget.backgroundOverlay(this.background);
            if (this.overlay == null) widget.overlay(IDrawable.NONE);
            else widget.overlay(this.overlay);
            widget.disableThemeBackground(this.disableThemeBackground);
            widget.widgetTheme(this.themeKey);
            widget.padding(this.padding.getLeft(), this.padding.getRight(), this.padding.getTop(), this.padding.getBottom());
            widget.margin(this.margin.getLeft(), this.margin.getRight(), this.margin.getTop(), this.margin.getBottom());
            if (widget instanceof TextWidget<?>) {
                TextWidget<?> text = (TextWidget<?>) widget;
                text.color(this.textColor == null ? null : () -> this.textColor);
                text.scale(this.textScale);
                if (this.textAlignment != null) text.textAlign(this.textAlignment);
            }
            if (widget instanceof ProgressWidget) {
                ProgressWidget progress = (ProgressWidget) widget;
                if (this.cssBar) progress.cssBar(this.cssTrackColor, this.cssFillColor);
                else progress.clearCssBar();
                progress.direction(this.progressDirection);
            }
            if (widget instanceof Flow) {
                Flow flow = (Flow) widget;
                flow.mainAxisAlignment(this.flowMain).crossAxisAlignment(this.flowCross)
                        .childPadding(this.flowGap).crossAxisChildPadding(this.flowCrossGap).wrap(this.flowWrap);
            } else if (widget instanceof Grid) {
                ((Grid) widget).setDomColumns(this.gridColumns);
            }
            if (widget instanceof AbstractScrollWidget) {
                AbstractScrollWidget<?, ?> scrollWidget = (AbstractScrollWidget<?, ?>) widget;
                scrollWidget.getScrollArea().setScrollDataX(this.scrollX);
                scrollWidget.getScrollArea().setScrollDataY(this.scrollY);
                scrollWidget.getScrollArea().setScrollBarBackgroundColor(this.scrollBarBackgroundColor);
                if (this.scrollX != null) {
                    this.scrollX.setScrollSpeed(this.scrollXSpeed);
                    this.scrollX.setCancelScrollEdge(this.scrollXCancelEdge);
                    this.scrollX.texture(this.scrollXTexture);
                }
                if (this.scrollY != null) {
                    this.scrollY.setScrollSpeed(this.scrollYSpeed);
                    this.scrollY.setCancelScrollEdge(this.scrollYCancelEdge);
                    this.scrollY.texture(this.scrollYTexture);
                }
            }
        }

    }
}

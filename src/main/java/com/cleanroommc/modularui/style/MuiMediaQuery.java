package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.markup.MuiMarkupException;

import java.util.Locale;

/** Supported media-condition subset: min/max width/height and orientation. */
public final class MuiMediaQuery {
    public static final MuiMediaQuery ALL = new MuiMediaQuery(null, null, null);
    private final Integer minWidth, maxWidth, minHeight;
    private final Integer maxHeight;
    private final String orientation;

    private MuiMediaQuery(Integer minWidth, Integer maxWidth, Integer minHeight, Integer maxHeight, String orientation) {
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.orientation = orientation;
    }

    private MuiMediaQuery(Integer minWidth, Integer maxWidth, String orientation) {
        this(minWidth, maxWidth, null, null, orientation);
    }

    public static MuiMediaQuery parse(String source) {
        String text = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || "all".equals(text) || "screen".equals(text) || "screen and all".equals(text)) return ALL;
        Integer minWidth = null, maxWidth = null, minHeight = null, maxHeight = null;
        String orientation = null;
        String[] terms = text.split("\\s+and\\s+");
        for (String raw : terms) {
            String term = raw.trim();
            if ("screen".equals(term) || "all".equals(term)) continue;
            if (term.startsWith("(") && term.endsWith(")")) term = term.substring(1, term.length() - 1).trim();
            int colon = term.indexOf(':');
            if (colon < 0) throw new MuiMarkupException("Unsupported @media condition: " + raw);
            String name = term.substring(0, colon).trim();
            String value = term.substring(colon + 1).trim();
            if ("orientation".equals(name)) {
                if (!("portrait".equals(value) || "landscape".equals(value))) throw new MuiMarkupException("Unsupported orientation: " + value);
                orientation = value;
            } else {
                int pixels = parsePixels(value);
                if ("min-width".equals(name)) minWidth = pixels;
                else if ("max-width".equals(name)) maxWidth = pixels;
                else if ("min-height".equals(name)) minHeight = pixels;
                else if ("max-height".equals(name)) maxHeight = pixels;
                else throw new MuiMarkupException("Unsupported @media feature: " + name);
            }
        }
        return new MuiMediaQuery(minWidth, maxWidth, minHeight, maxHeight, orientation);
    }

    public boolean matches(MuiMediaEnvironment environment) {
        if (minWidth != null && environment.getWidth() < minWidth) return false;
        if (maxWidth != null && environment.getWidth() > maxWidth) return false;
        if (minHeight != null && environment.getHeight() < minHeight) return false;
        if (maxHeight != null && environment.getHeight() > maxHeight) return false;
        if (orientation != null) {
            if ("__never__".equals(orientation)) return false;
            boolean landscape = environment.getWidth() >= environment.getHeight();
            if ("landscape".equals(orientation) != landscape) return false;
        }
        return true;
    }

    static MuiMediaQuery combine(MuiMediaQuery first, MuiMediaQuery second) {
        Integer minWidth = max(first.minWidth, second.minWidth);
        Integer maxWidth = min(first.maxWidth, second.maxWidth);
        Integer minHeight = max(first.minHeight, second.minHeight);
        Integer maxHeight = min(first.maxHeight, second.maxHeight);
        String orientation = first.orientation == null ? second.orientation : second.orientation == null ? first.orientation
                : first.orientation.equals(second.orientation) ? first.orientation : "__never__";
        return new MuiMediaQuery(minWidth, maxWidth, minHeight, maxHeight, orientation);
    }

    private static Integer max(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.max(first, second);
    }

    private static Integer min(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.min(first, second);
    }

    private static int parsePixels(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("px")) normalized = normalized.substring(0, normalized.length() - 2).trim();
        try { return Integer.parseInt(normalized); }
        catch (NumberFormatException exception) { throw new MuiMarkupException("Media dimensions must be integer pixels: " + value, exception); }
    }
}

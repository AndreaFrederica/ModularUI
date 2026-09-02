package com.cleanroommc.modularui.api.dom;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MuiElement extends MuiNode {

    private final String tagName;
    private final Map<String, String> mutableAttributes = new LinkedHashMap<>();
    private final Map<String, String> attributes = Collections.unmodifiableMap(this.mutableAttributes);

    MuiElement(MuiDocument document, NodeHandle handle, String tagName) {
        super(document, handle);
        this.tagName = normalizeName(tagName);
    }

    public String getTagName() {
        return this.tagName;
    }

    public @Nullable String getAttribute(String name) {
        requireAlive();
        return this.mutableAttributes.get(normalizeName(name));
    }

    public boolean hasAttribute(String name) {
        requireAlive();
        return this.mutableAttributes.containsKey(normalizeName(name));
    }

    public @UnmodifiableView Map<String, String> getAttributes() {
        requireAlive();
        return this.attributes;
    }

    public void setAttribute(String name, String value) {
        requireAlive();
        getOwnerDocument().setAttribute(this, normalizeName(name), Objects.requireNonNull(value, "value"));
    }

    public void removeAttribute(String name) {
        requireAlive();
        getOwnerDocument().setAttribute(this, normalizeName(name), null);
    }

    public @Nullable MuiElement querySelector(String selector) {
        return getOwnerDocument().querySelectorFrom(this, selector, false);
    }

    public java.util.List<MuiElement> querySelectorAll(String selector) {
        return getOwnerDocument().querySelectorAllFrom(this, selector, false);
    }

    /** Client-local horizontal viewport offset, equivalent to browser scrollLeft. */
    public int getScrollLeft() {
        requireAlive();
        return getOwnerDocument().getScrollLeft(this);
    }

    /** Client-local vertical viewport offset, equivalent to browser scrollTop. */
    public int getScrollTop() {
        requireAlive();
        return getOwnerDocument().getScrollTop(this);
    }

    public int getScrollWidth() {
        requireAlive();
        return getOwnerDocument().getScrollWidth(this);
    }

    public int getScrollHeight() {
        requireAlive();
        return getOwnerDocument().getScrollHeight(this);
    }

    public void scrollTo(int left, int top) {
        requireAlive();
        getOwnerDocument().setScrollPosition(this, left, top);
    }

    public void setScrollLeft(int left) {
        scrollTo(left, getScrollTop());
    }

    public void setScrollTop(int top) {
        scrollTo(getScrollLeft(), top);
    }

    boolean matchesSimpleSelector(String selector) {
        if ("*".equals(selector)) return true;
        if (selector.startsWith("#")) return selector.substring(1).equals(this.mutableAttributes.get("id"));
        if (selector.startsWith(".")) return hasClass(selector.substring(1));
        if (selector.startsWith("[") && selector.endsWith("]")) {
            String expression = selector.substring(1, selector.length() - 1).trim();
            int equals = expression.indexOf('=');
            if (equals < 0) return this.mutableAttributes.containsKey(normalizeName(expression));
            String name = normalizeName(expression.substring(0, equals).trim());
            String value = unquote(expression.substring(equals + 1).trim());
            return value.equals(this.mutableAttributes.get(name));
        }
        return this.tagName.equals(normalizeName(selector));
    }

    private boolean hasClass(String expected) {
        String classes = this.mutableAttributes.get("class");
        if (classes == null || expected.isEmpty()) return false;
        for (String value : classes.trim().split("\\s+")) {
            if (value.equals(expected)) return true;
        }
        return false;
    }

    void setAttributeDirect(String name, @Nullable String value) {
        if (value == null) this.mutableAttributes.remove(name);
        else this.mutableAttributes.put(name, value);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Name must not be empty");
        return normalized;
    }
}

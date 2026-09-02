package com.cleanroommc.modularui.api.component;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Describes one registered native DOM tag and its Widget projection. */
public final class MuiElementDescriptor<W extends IWidget> {

    public enum ChildModel { NONE, SINGLE, MULTIPLE, GRID }

    private final String tagName;
    private final Class<W> widgetType;
    private final MuiElementFactory<W> factory;
    private final ChildModel childModel;
    private final Map<String, MuiPropertyDescriptor<W, ?>> properties;

    private MuiElementDescriptor(Builder<W> builder) {
        this.tagName = normalizeTag(builder.tagName);
        this.widgetType = builder.widgetType;
        this.factory = builder.factory;
        this.childModel = builder.childModel;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
    }

    public static <W extends IWidget> Builder<W> builder(
            String tagName, Class<W> widgetType, MuiElementFactory<W> factory) {
        return new Builder<>(tagName, widgetType, factory);
    }

    public String getTagName() { return this.tagName; }
    public Class<W> getWidgetType() { return this.widgetType; }
    public ChildModel getChildModel() { return this.childModel; }
    public @UnmodifiableView Map<String, MuiPropertyDescriptor<W, ?>> getProperties() { return this.properties; }

    public W create(MuiElement element) {
        W widget = Objects.requireNonNull(this.factory.create(element),
                "Element factory returned null for " + this.tagName);
        if (!this.widgetType.isInstance(widget)) {
            throw new IllegalStateException("Element factory for " + this.tagName + " returned " + widget.getClass().getName());
        }
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            applyProperty(widget, attribute.getKey(), attribute.getValue());
        }
        return widget;
    }

    public void validateProperty(String name, @Nullable String value) {
        MuiPropertyDescriptor<W, ?> property = this.properties.get(MuiPropertyDescriptor.normalize(name));
        if (property != null) property.parse(value);
    }

    public boolean applyProperty(W widget, String name, @Nullable String value) {
        MuiPropertyDescriptor<W, ?> property = this.properties.get(MuiPropertyDescriptor.normalize(name));
        if (property == null) return false;
        property.apply(widget, value);
        return true;
    }

    @SuppressWarnings("unchecked")
    public boolean applyPropertyUnchecked(IWidget widget, String name, @Nullable String value) {
        if (!this.widgetType.isInstance(widget)) return false;
        return applyProperty((W) widget, name, value);
    }

    private static String normalizeTag(String tagName) {
        String normalized = Objects.requireNonNull(tagName, "tagName").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Tag name must not be empty");
        return normalized;
    }

    public static final class Builder<W extends IWidget> {

        private final String tagName;
        private final Class<W> widgetType;
        private final MuiElementFactory<W> factory;
        private final Map<String, MuiPropertyDescriptor<W, ?>> properties = new LinkedHashMap<>();
        private ChildModel childModel = ChildModel.NONE;

        private Builder(String tagName, Class<W> widgetType, MuiElementFactory<W> factory) {
            this.tagName = tagName;
            this.widgetType = Objects.requireNonNull(widgetType, "widgetType");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        public Builder<W> childModel(ChildModel childModel) {
            this.childModel = Objects.requireNonNull(childModel, "childModel");
            return this;
        }

        public <T> Builder<W> property(MuiPropertyDescriptor<W, T> property) {
            Objects.requireNonNull(property, "property");
            if (this.properties.putIfAbsent(property.getName(), property) != null) {
                throw new IllegalArgumentException("Duplicate property '" + property.getName() + "' for " + this.tagName);
            }
            return this;
        }

        public MuiElementDescriptor<W> build() {
            return new MuiElementDescriptor<>(this);
        }
    }
}

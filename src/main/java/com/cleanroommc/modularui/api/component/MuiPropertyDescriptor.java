package com.cleanroommc.modularui.api.component;

import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Typed bridge from one DOM attribute to a Widget property. */
public final class MuiPropertyDescriptor<W extends IWidget, T> {

    private final String name;
    private final MuiValueParser<T> parser;
    private final BiConsumer<W, T> applier;

    public MuiPropertyDescriptor(String name, MuiValueParser<T> parser, BiConsumer<W, T> applier) {
        this.name = normalize(name);
        this.parser = Objects.requireNonNull(parser, "parser");
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    public String getName() {
        return this.name;
    }

    public T parse(@Nullable String value) {
        try {
            return this.parser.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value for property '" + this.name + "': " + value, exception);
        }
    }

    public void apply(W widget, @Nullable String value) {
        this.applier.accept(widget, parse(value));
    }

    static String normalize(String name) {
        String normalized = Objects.requireNonNull(name, "name").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Property name must not be empty");
        return normalized;
    }
}

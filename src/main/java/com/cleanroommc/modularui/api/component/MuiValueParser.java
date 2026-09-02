package com.cleanroommc.modularui.api.component;

import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface MuiValueParser<T> {

    T parse(@Nullable String value);
}

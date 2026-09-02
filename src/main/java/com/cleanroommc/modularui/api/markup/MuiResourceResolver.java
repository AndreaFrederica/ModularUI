package com.cleanroommc.modularui.api.markup;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/** Resolves already-authorized MUI resources without exposing filesystem or URL access to the parser. */
@FunctionalInterface
public interface MuiResourceResolver {

    @Nullable InputStream open(String owner, String resourceId) throws IOException;
}

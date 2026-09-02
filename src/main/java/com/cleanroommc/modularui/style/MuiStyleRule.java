package com.cleanroommc.modularui.style;

import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable selector/style declaration pair. */
public final class MuiStyleRule {
    private final MuiSelector selector;
    private final Map<String, JsonElement> declarations;
    private final int order;
    private final MuiMediaQuery media;

    MuiStyleRule(MuiSelector selector, Map<String, JsonElement> declarations, int order) {
        this(selector, declarations, order, MuiMediaQuery.ALL);
    }

    MuiStyleRule(MuiSelector selector, Map<String, JsonElement> declarations, int order, MuiMediaQuery media) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
        this.order = order;
        this.media = Objects.requireNonNull(media, "media");
    }

    public MuiSelector getSelector() { return this.selector; }
    public Map<String, JsonElement> getDeclarations() { return this.declarations; }
    int getOrder() { return this.order; }
    public MuiMediaQuery getMedia() { return this.media; }
}

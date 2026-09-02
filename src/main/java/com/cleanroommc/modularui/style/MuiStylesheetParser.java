package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;

/** Bounded Gson parser for the MUI JSON stylesheet format (formatVersion 1). */
public final class MuiStylesheetParser {
    private MuiStylesheetParser() {}

    public static MuiCascade parse(String source) {
        if (source == null) throw new NullPointerException("source");
        if (source.length() > 256 * 1024) throw new MuiMarkupException("Stylesheet exceeds the configured size limit");
        try {
            JsonElement parsed = new JsonParser().parse(source);
            if (!parsed.isJsonObject()) throw new MuiMarkupException("Stylesheet root must be an object");
            return parseObject(parsed.getAsJsonObject());
        } catch (IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new MuiMarkupException("Invalid JSON stylesheet", exception);
        }
    }

    public static MuiCascade parse(InputStream source) throws IOException {
        if (source == null) throw new NullPointerException("source");
        return parse(read(source));
    }

    /** Parses the supported CSS subset through the same cascade used by JSON stylesheets. */
    public static MuiCascade parseCss(String source) {
        return MuiCssStylesheetParser.parse(source);
    }

    /** Parses a UTF-8 CSS stylesheet through the same cascade used by JSON stylesheets. */
    public static MuiCascade parseCss(InputStream source) throws IOException {
        return MuiCssStylesheetParser.parse(source);
    }

    /** Parses CSS and resolves only resources explicitly authorized by the supplied resolver. */
    public static MuiCascade parseCss(String owner, String source, MuiResourceResolver resolver) {
        return MuiCssStylesheetParser.parse(owner, source, resolver);
    }

    /** Parses UTF-8 CSS and resolves only resources explicitly authorized by the supplied resolver. */
    public static MuiCascade parseCss(String owner, InputStream source, MuiResourceResolver resolver) throws IOException {
        return MuiCssStylesheetParser.parse(owner, source, resolver);
    }

    private static MuiCascade parseObject(JsonObject root) {
        int version = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 1;
        if (version != 1) throw new MuiMarkupException("Unsupported stylesheet formatVersion: " + version);
        Map<String, JsonElement> variables = new LinkedHashMap<>();
        if (root.has("variables")) {
            if (!root.get("variables").isJsonObject()) throw new MuiMarkupException("stylesheet.variables must be an object");
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variables").entrySet()) {
                if (!entry.getKey().startsWith("--")) throw new MuiMarkupException("Style variable must start with --: " + entry.getKey());
                variables.put(entry.getKey(), entry.getValue());
            }
        }
        if (!root.has("rules") || !root.get("rules").isJsonArray()) throw new MuiMarkupException("stylesheet.rules must be an array");
        JsonArray rulesJson = root.getAsJsonArray("rules");
        if (rulesJson.size() > 4096) throw new MuiMarkupException("Stylesheet has too many rules");
        ArrayList<MuiStyleRule> rules = new ArrayList<>();
        for (int i = 0; i < rulesJson.size(); i++) {
            JsonObject rule = rulesJson.get(i).getAsJsonObject();
            String selector = rule.has("selector") ? rule.get("selector").getAsString() : null;
            if (selector == null) throw new MuiMarkupException("Style rule is missing selector");
            JsonElement style = rule.get("style");
            if (style == null || !style.isJsonObject()) throw new MuiMarkupException("Style rule must contain an object style");
            if (style.getAsJsonObject().entrySet().size() > 256) throw new MuiMarkupException("Style rule has too many properties");
            Map<String, JsonElement> declarations = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> property : style.getAsJsonObject().entrySet()) {
                String name = property.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                if (name.isEmpty()) throw new MuiMarkupException("Style property name must not be empty");
                declarations.put(name, property.getValue());
            }
            try {
                rules.add(new MuiStyleRule(MuiSelector.parse(selector), declarations, i));
            } catch (IllegalArgumentException exception) {
                throw new MuiMarkupException("Invalid stylesheet selector: " + selector, exception);
            }
        }
        return new MuiCascade(rules, variables);
    }

    private static String read(InputStream source) throws IOException {
        InputStreamReader reader = new InputStreamReader(source, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, count);
            if (result.length() > 256 * 1024) throw new MuiMarkupException("Stylesheet exceeds the configured size limit");
        }
        return result.toString();
    }
}

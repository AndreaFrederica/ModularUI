package com.cleanroommc.modularui.markup;

import com.cleanroommc.modularui.api.markup.MuiApplicationDescriptor;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Bounded parser for one declarative MUI application registration file. */
public final class MuiApplicationDescriptorParser {

    public static final int FORMAT_VERSION = 1;
    public static final int MAX_SOURCE_CHARS = 256 * 1024;
    public static final int MAX_NAMESPACES = 64;
    public static final int MAX_COMPONENTS = 1024;
    public static final int MAX_STYLESHEETS = 128;

    private static final Set<String> ROOT_FIELDS = new HashSet<>(Arrays.asList(
            "formatVersion", "owner", "namespaces", "components", "stylesheets"));

    private MuiApplicationDescriptorParser() {}

    public static MuiApplicationDescriptor parse(InputStream source) throws IOException {
        if (source == null) throw new NullPointerException("source");
        return parse(read(source));
    }

    public static MuiApplicationDescriptor parse(String source) {
        if (source == null) throw new NullPointerException("source");
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new MuiMarkupException("MUI application descriptor exceeds the size limit");
        }
        try {
            JsonElement parsed = new JsonParser().parse(source);
            if (!parsed.isJsonObject()) throw failure("MUI application descriptor root must be an object");
            JsonObject root = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> field : root.entrySet()) {
                if (!ROOT_FIELDS.contains(field.getKey())) {
                    throw failure("Unknown MUI application descriptor field: " + field.getKey());
                }
            }
            int version = integer(required(root, "formatVersion"), "formatVersion");
            if (version != FORMAT_VERSION) {
                throw failure("Unsupported MUI application descriptor format version: " + version);
            }
            MuiApplicationDescriptor.Builder builder = MuiApplicationDescriptor.builder(
                    string(required(root, "owner"), "owner"));
            objectEntries(root, "namespaces", MAX_NAMESPACES, builder::namespace);
            objectEntries(root, "components", MAX_COMPONENTS, builder::component);
            JsonElement stylesheets = root.get("stylesheets");
            if (stylesheets != null) {
                if (!stylesheets.isJsonArray()) throw failure("stylesheets must be an array");
                JsonArray values = stylesheets.getAsJsonArray();
                if (values.size() > MAX_STYLESHEETS) throw failure("Too many registered stylesheets");
                for (JsonElement value : values) builder.stylesheet(string(value, "stylesheet"));
            }
            return builder.build();
        } catch (MuiMarkupException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MuiMarkupException("Invalid MUI application descriptor JSON", exception);
        }
    }

    private static void objectEntries(JsonObject root, String name, int limit, EntryConsumer consumer) {
        JsonElement element = root.get(name);
        if (element == null) return;
        if (!element.isJsonObject()) throw failure(name + " must be an object");
        JsonObject values = element.getAsJsonObject();
        if (values.size() > limit) throw failure("Too many registered " + name);
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            consumer.accept(entry.getKey(), string(entry.getValue(), name + " value"));
        }
    }

    private static JsonElement required(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null) throw failure("Missing MUI application descriptor field: " + name);
        return value;
    }

    private static String string(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw failure(name + " must be a string");
        }
        String result = value.getAsString().trim();
        if (result.isEmpty()) throw failure(name + " must not be empty");
        return result;
    }

    private static int integer(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw failure(name + " must be an integer");
        }
        int result = value.getAsInt();
        if (result < 0 || value.getAsDouble() != result) throw failure(name + " must be a non-negative integer");
        return result;
    }

    private static String read(InputStream source) throws IOException {
        Reader reader = new InputStreamReader(source, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            if (result.length() + count > MAX_SOURCE_CHARS) {
                throw new MuiMarkupException("MUI application descriptor exceeds the size limit");
            }
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static MuiMarkupException failure(String message) {
        return new MuiMarkupException(message);
    }

    @FunctionalInterface
    private interface EntryConsumer {
        void accept(String key, String value);
    }
}

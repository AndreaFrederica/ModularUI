package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded parser for the MUI CSS subset, including safe {@code @media} and {@code @import}. */
public final class MuiCssStylesheetParser {
    private static final int MAX_LENGTH = 256 * 1024;
    private static final int MAX_TOTAL_LENGTH = 1024 * 1024;
    private static final int MAX_RULES = 4096;
    private static final int MAX_DECLARATIONS = 256;
    private static final int MAX_IMPORTS = 128;
    private static final int MAX_IMPORT_DEPTH = 16;

    private MuiCssStylesheetParser() {}

    public static MuiCascade parse(String source) { return parseInternal(null, source, null); }

    public static MuiCascade parse(String owner, String source, MuiResourceResolver resolver) {
        if (owner == null || resolver == null) throw new NullPointerException();
        return parseInternal(owner, source, resolver);
    }

    public static MuiCascade parse(InputStream source) throws IOException { return parse(read(source)); }

    public static MuiCascade parse(String owner, InputStream source, MuiResourceResolver resolver) throws IOException {
        if (owner == null || resolver == null) throw new NullPointerException();
        return parseInternal(owner, read(source), resolver);
    }

    private static MuiCascade parseInternal(String owner, String source, MuiResourceResolver resolver) {
        if (source == null) throw new NullPointerException("source");
        if (source.length() > MAX_LENGTH) throw new MuiMarkupException("Stylesheet exceeds the configured size limit");
        Accumulator accumulator = new Accumulator(owner, resolver);
        accumulator.parseSource("<inline>", source, MuiMediaQuery.ALL, 0);
        return new MuiCascade(accumulator.rules, accumulator.variables);
    }

    private static final class Accumulator {
        private final String owner;
        private final MuiResourceResolver resolver;
        private final List<MuiStyleRule> rules = new ArrayList<>();
        private final Map<String, JsonElement> variables = new LinkedHashMap<>();
        private final Deque<String> importStack = new ArrayDeque<>();
        private int totalLength;
        private int imports;
        private int order;

        private Accumulator(String owner, MuiResourceResolver resolver) {
            this.owner = owner;
            this.resolver = resolver;
        }

        private void parseSource(String resourceId, String source, MuiMediaQuery media, int depth) {
            if (depth > MAX_IMPORT_DEPTH) throw new MuiMarkupException("CSS @import nesting exceeds the configured depth limit");
            if (source.length() > MAX_LENGTH || (this.totalLength += source.length()) > MAX_TOTAL_LENGTH) {
                throw new MuiMarkupException("Combined CSS imports exceed the configured size limit");
            }
            String css = stripComments(source);
            if (!this.importStack.add(resourceId)) throw new MuiMarkupException("Cyclic CSS @import: " + resourceId);
            try { parseRange(css, 0, css.length(), media, depth); }
            finally { this.importStack.removeLast(); }
        }

        private void parseRange(String css, int start, int end, MuiMediaQuery media, int depth) {
            int cursor = start;
            while (true) {
                cursor = skipWhitespace(css, cursor, end);
                if (cursor >= end) return;
                int open = findOutside(css, '{', cursor, end);
                int semicolon = findOutside(css, ';', cursor, end);
                if (semicolon >= 0 && (open < 0 || semicolon < open)) {
                    String statement = css.substring(cursor, semicolon).trim();
                    if (!isImportStatement(statement)) throw error("Unsupported CSS statement", css, cursor);
                    parseImport(statement, media, depth);
                    cursor = semicolon + 1;
                    continue;
                }
                if (open < 0) throw error("CSS rule is missing '{'", css, cursor);
                int close = matchingBrace(css, open, end);
                if (close < 0) throw error("CSS rule is missing '}'", css, open);
                String header = css.substring(cursor, open).trim();
                if (header.toLowerCase(Locale.ROOT).startsWith("@media")) {
                    parseRange(css, open + 1, close, combine(media, MuiMediaQuery.parse(header.substring(6).trim())), depth);
                } else if (header.charAt(0) == '@') {
                    throw error("Unsupported CSS at-rule: " + header, css, cursor);
                } else {
                    parseRule(header, css.substring(open + 1, close), media, css, cursor);
                }
                cursor = close + 1;
            }
        }

        private void parseRule(String selectorSource, String block, MuiMediaQuery media, String source, int offset) {
            Map<String, JsonElement> declarations = parseDeclarations(block);
            if (declarations.size() > MAX_DECLARATIONS) throw error("CSS rule has too many declarations", source, offset);
            for (String selector : splitOutside(selectorSource, ',')) {
                if (rules.size() >= MAX_RULES) throw error("Stylesheet has too many rules", source, offset);
                Map<String, JsonElement> ruleDeclarations = new LinkedHashMap<>(declarations);
                if (":root".equals(selector.trim())) {
                    for (Map.Entry<String, JsonElement> entry : declarations.entrySet()) {
                        if (entry.getKey().startsWith("--")) variables.put(entry.getKey(), entry.getValue());
                    }
                    ruleDeclarations.entrySet().removeIf(entry -> entry.getKey().startsWith("--"));
                }
                try {
                    rules.add(new MuiStyleRule(MuiSelector.parse(selector), ruleDeclarations, order++, media));
                } catch (IllegalArgumentException exception) {
                    throw new MuiMarkupException("Invalid CSS selector: " + selector, exception);
                }
            }
        }

        private void parseImport(String statement, MuiMediaQuery inheritedMedia, int depth) {
            if (resolver == null) throw new MuiMarkupException("CSS @import requires an authorized resource resolver");
            String value = statement.substring(7).trim();
            String resourceId;
            String mediaSource;
            if (value.startsWith("\"") || value.startsWith("'")) {
                char quote = value.charAt(0);
                int closing = findClosingQuote(value, quote, 1);
                if (closing < 0) throw new MuiMarkupException("CSS @import resource quote is not closed");
                resourceId = value.substring(1, closing);
                mediaSource = value.substring(closing + 1).trim();
            } else if (value.regionMatches(true, 0, "url(", 0, 4)) {
                int closing = findClosingParen(value, 4);
                if (closing < 0) throw new MuiMarkupException("CSS @import url() is not closed");
                resourceId = unquote(value.substring(4, closing).trim());
                mediaSource = value.substring(closing + 1).trim();
            } else {
                throw new MuiMarkupException("CSS @import requires a quoted resource id or url()");
            }
            if (resourceId.isEmpty() || resourceId.indexOf('\u0000') >= 0 || resourceId.indexOf(';') >= 0) {
                throw new MuiMarkupException("CSS @import resource id is invalid");
            }
            MuiMediaQuery importMedia = mediaSource.isEmpty() ? MuiMediaQuery.ALL : MuiMediaQuery.parse(mediaSource);
            if (++imports > MAX_IMPORTS) throw new MuiMarkupException("Stylesheet has too many @import resources");
            try (InputStream input = resolver.open(owner, resourceId)) {
                if (input == null) throw new MuiMarkupException("CSS @import resource was not found: " + resourceId);
                parseSource(resourceId, read(input), combine(inheritedMedia, importMedia), depth + 1);
            } catch (IOException exception) {
                throw new MuiMarkupException("Failed to read CSS @import resource: " + resourceId, exception);
            }
        }
    }

    private static boolean isImportStatement(String statement) {
        if (statement.length() < 7 || !statement.regionMatches(true, 0, "@import", 0, 7)) return false;
        return statement.length() == 7 || Character.isWhitespace(statement.charAt(7));
    }

    private static int findClosingQuote(String source, char quote, int start) {
        for (int i = start; i < source.length(); i++) {
            if (source.charAt(i) == quote && source.charAt(i - 1) != '\\') return i;
        }
        return -1;
    }

    private static int findClosingParen(String source, int start) {
        char quote = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote && source.charAt(i - 1) != '\\') quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ')') {
                return i;
            }
        }
        return -1;
    }

    private static MuiMediaQuery combine(MuiMediaQuery first, MuiMediaQuery second) {
        if (first == MuiMediaQuery.ALL) return second;
        if (second == MuiMediaQuery.ALL) return first;
        return MuiMediaQuery.combine(first, second);
    }

    private static Map<String, JsonElement> parseDeclarations(String block) {
        Map<String, JsonElement> declarations = new LinkedHashMap<>();
        for (String declaration : splitOutside(block, ';')) {
            String text = declaration.trim();
            if (text.isEmpty()) continue;
            int colon = findOutside(text, ':', 0, text.length());
            if (colon < 0) throw new MuiMarkupException("CSS declaration is missing ':'");
            String name = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = text.substring(colon + 1).trim();
            if (name.isEmpty() || value.isEmpty()) throw new MuiMarkupException("CSS declaration has an empty name or value");
            declarations.put(name, new JsonPrimitive(value));
        }
        return declarations;
    }

    private static List<String> splitOutside(String source, char delimiter) {
        List<String> result = new ArrayList<>();
        int start = 0, parentheses = 0, brackets = 0;
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || source.charAt(i - 1) != '\\')) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            else if (c == delimiter && parentheses == 0 && brackets == 0) {
                result.add(source.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (quote != 0 || parentheses != 0 || brackets != 0) throw new MuiMarkupException("Malformed CSS declaration or selector");
        result.add(source.substring(start).trim());
        return result;
    }

    private static int findOutside(String source, char expected, int start, int end) {
        int parentheses = 0, brackets = 0;
        char quote = 0;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || source.charAt(i - 1) != '\\')) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            else if (c == expected && parentheses == 0 && brackets == 0) return i;
        }
        return -1;
    }

    private static int matchingBrace(String source, int open, int end) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < end; i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || source.charAt(i - 1) != '\\')) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static int skipWhitespace(String source, int start, int end) {
        int i = start;
        while (i < end && Character.isWhitespace(source.charAt(i))) i++;
        return i;
    }

    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            if (i + 1 < source.length() && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) throw new MuiMarkupException("CSS comment is not closed");
                i = end + 2;
            } else result.append(source.charAt(i++));
        }
        return result.toString();
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String read(InputStream source) throws IOException {
        if (source == null) throw new NullPointerException("source");
        InputStreamReader reader = new InputStreamReader(source, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, count);
            if (result.length() > MAX_LENGTH) throw new MuiMarkupException("Stylesheet exceeds the configured size limit");
        }
        return result.toString();
    }

    private static MuiMarkupException error(String message, String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) if (source.charAt(i) == '\n') line++;
        return new MuiMarkupException(message + " at line " + line);
    }
}

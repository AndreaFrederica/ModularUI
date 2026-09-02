package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Small, deterministic selector implementation for the MUI stylesheet subset. */
public final class MuiSelector {
    private enum Combinator { DESCENDANT, CHILD }
    private final List<Part> parts;
    private final List<Combinator> combinators;
    private final int specificity;
    private final String source;

    private MuiSelector(String source, List<Part> parts, List<Combinator> combinators, int specificity) {
        this.source = source;
        this.parts = Collections.unmodifiableList(parts);
        this.combinators = Collections.unmodifiableList(combinators);
        this.specificity = specificity;
    }

    public static MuiSelector parse(String selector) {
        String source = Objects.requireNonNull(selector, "selector").trim();
        if (source.isEmpty()) throw new IllegalArgumentException("Selector must not be empty");
        List<String> tokens = tokenize(source);
        if (">".equals(tokens.get(tokens.size() - 1))) throw new IllegalArgumentException("Selector cannot end with >");
        List<Part> parts = new ArrayList<>();
        List<Combinator> combinators = new ArrayList<>();
        int specificity = 0;
        Combinator pending = Combinator.DESCENDANT;
        for (String token : tokens) {
            if (">".equals(token)) {
                pending = Combinator.CHILD;
                continue;
            }
            if (!parts.isEmpty()) combinators.add(pending);
            Part part = Part.parse(token);
            parts.add(part);
            specificity += part.specificity;
            pending = Combinator.DESCENDANT;
        }
        if (parts.isEmpty() || combinators.size() != parts.size() - 1) {
            throw new IllegalArgumentException("Invalid selector: " + source);
        }
        return new MuiSelector(source, parts, combinators, specificity);
    }

    public String getSource() { return this.source; }
    public int getSpecificity() { return this.specificity; }

    public boolean matches(MuiElement element) {
        return matchesPart(element, parts.size() - 1);
    }

    private boolean matchesPart(MuiElement element, int index) {
        if (element == null || !parts.get(index).matches(element)) return false;
        if (index == 0) return true;
        MuiNode parent = element.getParentNode();
        if (combinators.get(index - 1) == Combinator.CHILD) {
            return parent instanceof MuiElement && matchesPart((MuiElement) parent, index - 1);
        }
        while (parent instanceof MuiElement) {
            if (matchesPart((MuiElement) parent, index - 1)) return true;
            parent = parent.getParentNode();
        }
        return false;
    }

    private static List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int brackets = 0;
        char quote = 0;
        boolean whitespace = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) quote = 0;
            } else if (brackets > 0 && (c == '\'' || c == '"')) {
                quote = c;
                current.append(c);
            } else if (c == '[') {
                brackets++;
                current.append(c);
            } else if (c == ']') {
                brackets--;
                if (brackets < 0) throw new IllegalArgumentException("Unmatched selector bracket");
                current.append(c);
            } else if (brackets == 0 && c == '>') {
                flush(tokens, current);
                tokens.add(">");
                whitespace = false;
            } else if (brackets == 0 && Character.isWhitespace(c)) {
                if (current.length() > 0) flush(tokens, current);
                whitespace = true;
            } else {
                if (whitespace && current.length() == 0 && !tokens.isEmpty()
                        && !">".equals(tokens.get(tokens.size() - 1))) {
                    // The default descendant combinator is represented by the absence of a token.
                }
                current.append(c);
                whitespace = false;
            }
        }
        if (quote != 0 || brackets != 0) throw new IllegalArgumentException("Malformed selector: " + source);
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static final class Part {
        private final String tag;
        private final String id;
        private final List<String> classes;
        private final List<Attribute> attributes;
        private final List<String> pseudo;
        private final int specificity;

        private Part(String tag, String id, List<String> classes, List<Attribute> attributes,
                     List<String> pseudo, int specificity) {
            this.tag = tag;
            this.id = id;
            this.classes = classes;
            this.attributes = attributes;
            this.pseudo = pseudo;
            this.specificity = specificity;
        }

        static Part parse(String token) {
            String tag = null, id = null;
            List<String> classes = new ArrayList<>();
            List<Attribute> attributes = new ArrayList<>();
            List<String> pseudo = new ArrayList<>();
            int i = 0, specificity = 0;
            while (i < token.length() && ".#[ :".indexOf(token.charAt(i)) < 0) i++;
            if (i > 0) {
                tag = token.substring(0, i).toLowerCase(Locale.ROOT);
                if ("*".equals(tag)) tag = null;
                else specificity++;
            }
            while (i < token.length()) {
                char marker = token.charAt(i++);
                if (marker == '#') {
                    int end = nextMarker(token, i);
                    if (id != null) throw new IllegalArgumentException("Duplicate id selector");
                    id = token.substring(i, end);
                    if (id.isEmpty()) throw new IllegalArgumentException("Empty id selector");
                    specificity += 100;
                    i = end;
                } else if (marker == '.') {
                    int end = nextMarker(token, i);
                    String value = token.substring(i, end);
                    if (value.isEmpty()) throw new IllegalArgumentException("Empty class selector");
                    classes.add(value);
                    specificity += 10;
                    i = end;
                } else if (marker == '[') {
                    int end = token.indexOf(']', i);
                    if (end < 0) throw new IllegalArgumentException("Unclosed attribute selector");
                    String expression = token.substring(i, end).trim();
                    int equals = expression.indexOf('=');
                    String name = equals < 0 ? expression : expression.substring(0, equals).trim();
                    String value = equals < 0 ? null : unquote(expression.substring(equals + 1).trim());
                    if (name.isEmpty()) throw new IllegalArgumentException("Empty attribute selector");
                    attributes.add(new Attribute(name, value));
                    specificity += 10;
                    i = end + 1;
                } else if (marker == ':') {
                    int end = nextMarker(token, i);
                    String value = token.substring(i, end).toLowerCase(Locale.ROOT);
                    if (!(value.equals("hover") || value.equals("active") || value.equals("focus")
                            || value.equals("focus-visible") || value.equals("disabled") || value.equals("checked")
                            || value.equals("root"))) {
                        throw new IllegalArgumentException("Unsupported pseudo selector: " + value);
                    }
                    pseudo.add(value);
                    specificity += 10;
                    i = end;
                } else {
                    throw new IllegalArgumentException("Invalid selector token: " + token);
                }
            }
            return new Part(tag, id, classes, attributes, pseudo, specificity);
        }

        boolean matches(MuiElement element) {
            if (tag != null && !localTag(element.getTagName()).equals(localTag(tag))) return false;
            if (id != null && !id.equals(element.getAttribute("id"))) return false;
            for (String expected : classes) {
                String value = element.getAttribute("class");
                if (value == null) return false;
                boolean found = false;
                for (String actual : value.trim().split("\\s+")) if (expected.equals(actual)) found = true;
                if (!found) return false;
            }
            for (Attribute attribute : attributes) {
                if (!element.hasAttribute(attribute.name)) return false;
                if (attribute.value != null && !attribute.value.equals(element.getAttribute(attribute.name))) return false;
            }
            for (String state : pseudo) if (!matchesState(element, state)) return false;
            return true;
        }

        private static boolean matchesState(MuiElement element, String state) {
            if (state.equals("root")) return element.getParentNode() == null
                    || "mui:component-root".equals(element.getParentNode() instanceof MuiElement
                    ? ((MuiElement) element.getParentNode()).getTagName() : "");
            if (state.equals("disabled")) return "false".equals(element.getAttribute("enabled"))
                    || element.hasAttribute("disabled");
            if (state.equals("checked")) return "true".equals(element.getAttribute("checked"));
            return "true".equals(element.getAttribute("data-" + state)) || "true".equals(element.getAttribute(state));
        }

        private static int nextMarker(String token, int start) {
            int i = start;
            while (i < token.length() && ".#[ :".indexOf(token.charAt(i)) < 0) i++;
            return i;
        }

        private static String unquote(String value) {
            return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))
                    ? value.substring(1, value.length() - 1) : value;
        }
    }

    private static String localTag(String tag) {
        String value = tag.toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        int pipe = value.indexOf('|');
        int split = colon >= 0 ? colon : pipe;
        return split >= 0 ? value.substring(split + 1) : value;
    }

    private static final class Attribute {
        private final String name;
        private final String value;
        private Attribute(String name, String value) {
            this.name = name.trim().toLowerCase(Locale.ROOT);
            this.value = value;
        }
    }
}

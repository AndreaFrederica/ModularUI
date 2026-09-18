package com.cleanroommc.modularui.markup;

import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;
import com.cleanroommc.modularui.api.sync.MuiProtocolPlan;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Secure common-side parser for fixed protocol descriptors. It never creates a DOM. */
public final class MuiProtocolXmlParser {

    public static final int MAX_SOURCE_CHARS = 256 * 1024;
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_COMPONENT_DEPTH = 32;

    private MuiProtocolXmlParser() {}

    public static MuiProtocolPlan parse(String source) {
        return parseInternal(null, source, null);
    }

    /** Parses a protocol and expands authorized common-side {@code <component>} resources. */
    public static MuiProtocolPlan parse(String owner, String source, MuiResourceResolver resolver) {
        if (owner == null) throw new NullPointerException("owner");
        if (resolver == null) throw new NullPointerException("resolver");
        return parseInternal(owner, source, resolver);
    }

    private static MuiProtocolPlan parseInternal(String owner, String source, MuiResourceResolver resolver) {
        if (source == null) throw new NullPointerException("source");
        ParseState state = new ParseState(owner, resolver);
        parseDocument(source, state, true, 0);
        if (state.builder == null) throw new MuiMarkupException("Protocol XML is missing its root");
        return state.builder.build();
    }

    private static void parseDocument(String source, ParseState state, boolean protocolRoot, int ordinalOffset) {
        state.sourceChars += source.length();
        if (state.sourceChars > MAX_SOURCE_CHARS) {
            throw new MuiMarkupException("Protocol XML and components exceed the size limit");
        }
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(new StringReader(source));
            int depth = 0;
            boolean sawRoot = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) fail(reader, "DOCTYPE/DTD is not permitted in protocol XML");
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String name = reader.getLocalName();
                    Map<String, String> attributes = attributes(reader);
                    if (depth == 1) {
                        if (sawRoot) fail(reader, "Protocol XML must contain exactly one root element");
                        sawRoot = true;
                        if (protocolRoot) {
                            if (!"protocol".equals(name) && !"mui-protocol".equals(name)) {
                                fail(reader, "Protocol XML root must be <protocol>");
                            }
                            requireOnly(reader, attributes, "id", "schema-version");
                            if (state.builder != null) fail(reader, "Protocol XML contains more than one protocol root");
                            state.builder = MuiProtocolPlan.builder(required(reader, attributes, "id"),
                                    integer(reader, required(reader, attributes, "schema-version"), "schema-version"));
                        } else {
                            if (!"protocol-component".equals(name)) {
                                fail(reader, "Protocol component root must be <protocol-component>");
                            }
                            requireOnly(reader, attributes);
                        }
                    } else if (depth == 2) {
                        if (state.builder == null) fail(reader, "Protocol XML is missing its root");
                        if ("component".equals(name)) {
                            includeComponent(reader, state, attributes, ordinalOffset);
                        } else if ("slot-range".equals(name)) {
                            expandSlotRange(reader, state, attributes, ordinalOffset);
                        } else {
                            if (++state.entries > MAX_ENTRIES) {
                                fail(reader, "Expanded protocol entry count exceeds the limit");
                            }
                            addEntry(reader, state.builder, name, attributes, ordinalOffset);
                        }
                    } else {
                        fail(reader, "Protocol entries and component references cannot contain child elements");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && !reader.getText().trim().isEmpty()) {
                    fail(reader, "Protocol XML cannot contain text");
                }
            }
            if (!sawRoot || depth != 0) throw new MuiMarkupException(
                    protocolRoot ? "Protocol XML must contain one complete root"
                            : "Protocol component must contain one complete root");
        } catch (XMLStreamException exception) {
            throw new MuiMarkupException("Invalid protocol XML", line(reader), column(reader), exception);
        } catch (IllegalArgumentException exception) {
            throw new MuiMarkupException(exception.getMessage(), line(reader), column(reader), exception);
        } finally {
            if (reader != null) {
                try { reader.close(); }
                catch (XMLStreamException ignored) {}
            }
        }
    }

    private static void includeComponent(XMLStreamReader reader, ParseState state,
                                         Map<String, String> attributes, int parentOrdinalOffset) {
        requireOnly(reader, attributes, "src", "ordinal-offset");
        if (state.resolver == null) {
            fail(reader, "Protocol components require an authorized resource resolver");
        }
        String resource = required(reader, attributes, "src");
        int localOffset = optionalInteger(reader, attributes, "ordinal-offset", 0);
        int ordinalOffset = addOrdinal(reader, parentOrdinalOffset, localOffset);
        if (state.components.size() >= MAX_COMPONENT_DEPTH) {
            fail(reader, "Protocol component expansion exceeds the depth limit");
        }
        if (state.components.contains(resource)) {
            fail(reader, "Recursive protocol component reference: " + resource);
        }

        state.components.push(resource);
        try (InputStream stream = state.resolver.open(state.owner, resource)) {
            if (stream == null) fail(reader, "Protocol component resource not found: " + resource);
            parseDocument(readUtf8(stream), state, false, ordinalOffset);
        } catch (IOException exception) {
            throw new MuiMarkupException("Unable to read protocol component " + resource,
                    line(reader), column(reader), exception);
        } finally {
            state.components.pop();
        }
    }

    private static void addEntry(XMLStreamReader reader, MuiProtocolPlan.Builder builder,
                                 String name, Map<String, String> attributes, int ordinalOffset) {
        String key = required(reader, attributes, "key");
        String type = required(reader, attributes, "type");
        int version = integer(reader, required(reader, attributes, "version"), "version");
        switch (name) {
            case "handler":
                requireOnly(reader, attributes, "key", "id", "type", "version");
                builder.handler(key, optionalInteger(reader, attributes, "id", 0), type, version);
                break;
            case "action":
                requireOnly(reader, attributes, "key", "type", "version");
                builder.action(key, type, version);
                break;
            case "slot":
                requireOnly(reader, attributes, "key", "id", "type", "version", "ordinal");
                int ordinal = integer(reader, required(reader, attributes, "ordinal"), "ordinal");
                builder.slot(key, optionalInteger(reader, attributes, "id", 0), type, version,
                        addOrdinal(reader, ordinalOffset, ordinal));
                break;
            default:
                fail(reader, "Unknown protocol entry <" + name + ">");
        }
    }

    private static int addOrdinal(XMLStreamReader reader, int first, int second) {
        if (first > Integer.MAX_VALUE - second) fail(reader, "Protocol slot ordinal exceeds the integer limit");
        return first + second;
    }

    private static void expandSlotRange(XMLStreamReader reader, ParseState state,
                                        Map<String, String> attributes, int ordinalOffset) {
        requireOnly(reader, attributes, "prefix", "count", "type", "version", "ordinal");
        String prefix = required(reader, attributes, "prefix");
        int count = integer(reader, required(reader, attributes, "count"), "count");
        int version = integer(reader, required(reader, attributes, "version"), "version");
        int first = integer(reader, required(reader, attributes, "ordinal"), "ordinal");
        String type = required(reader, attributes, "type");
        if (count <= 0 || count > MAX_ENTRIES) fail(reader, "Invalid slot range count");
        if (count > MAX_ENTRIES - state.entries) {
            fail(reader, "Expanded protocol entry count exceeds the limit");
        }
        int start = addOrdinal(reader, ordinalOffset, first);
        addOrdinal(reader, start, count - 1);
        state.entries += count;
        for (int i = 0; i < count; i++) state.builder.slot(prefix + i, 0, type, version, start + i);
    }


    private static Map<String, String> attributes(XMLStreamReader reader) {
        if (reader.getAttributeCount() > 8) fail(reader, "Protocol XML element has too many attributes");
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String name = reader.getAttributeLocalName(i);
            if (values.put(name, reader.getAttributeValue(i)) != null) fail(reader, "Duplicate protocol attribute: " + name);
        }
        return values;
    }

    private static String required(XMLStreamReader reader, Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.trim().isEmpty()) fail(reader, "Missing protocol attribute: " + name);
        return value.trim();
    }

    private static int optionalInteger(XMLStreamReader reader, Map<String, String> values, String name, int fallback) {
        String value = values.get(name);
        return value == null ? fallback : integer(reader, value, name);
    }

    private static int integer(XMLStreamReader reader, String value, String name) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) fail(reader, "Protocol attribute must not be negative: " + name);
            return parsed;
        } catch (NumberFormatException exception) {
            throw new MuiMarkupException("Invalid integer protocol attribute: " + name,
                    line(reader), column(reader), exception);
        }
    }

    private static void requireOnly(XMLStreamReader reader, Map<String, String> values, String... allowed) {
        Set<String> names = new HashSet<>();
        java.util.Collections.addAll(names, allowed);
        for (String name : values.keySet()) if (!names.contains(name)) fail(reader, "Unknown protocol attribute: " + name);
    }

    private static String readUtf8(InputStream stream) throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        return result.toString();
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        set(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        set(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static void set(XMLInputFactory factory, String property, Object value) {
        try { factory.setProperty(property, value); }
        catch (IllegalArgumentException ignored) {}
    }

    private static void fail(XMLStreamReader reader, String message) {
        throw new MuiMarkupException(message, line(reader), column(reader), null);
    }

    private static int line(XMLStreamReader reader) {
        return reader == null || reader.getLocation() == null ? -1 : reader.getLocation().getLineNumber();
    }

    private static int column(XMLStreamReader reader) {
        return reader == null || reader.getLocation() == null ? -1 : reader.getLocation().getColumnNumber();
    }

    private static final class ParseState {
        private final String owner;
        private final MuiResourceResolver resolver;
        private final Deque<String> components = new ArrayDeque<>();
        private MuiProtocolPlan.Builder builder;
        private int sourceChars;
        private int entries;

        private ParseState(String owner, MuiResourceResolver resolver) {
            this.owner = owner;
            this.resolver = resolver;
        }
    }
}

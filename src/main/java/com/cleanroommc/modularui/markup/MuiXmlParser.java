package com.cleanroommc.modularui.markup;

import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.MuiText;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded, namespace-preserving StAX parser that compiles XML structure into the existing Java DOM. */
public final class MuiXmlParser {

    private MuiXmlParser() {}

    public static MuiElement parse(String source, MuiDocument document) {
        return parse(source, document, Limits.DEFAULT);
    }

    public static MuiElement parse(String source, MuiDocument document, Limits limits) {
        if (source == null) throw new NullPointerException("source");
        if (document == null) throw new NullPointerException("document");
        if (limits == null) throw new NullPointerException("limits");
        if (source.length() > limits.maxSourceChars) {
            throw new MuiMarkupException("XML source exceeds the configured size limit");
        }

        XMLStreamReader reader = null;
        MutationScope mutation = document.beginMutation();
        boolean committed = false;
        Deque<MuiElement> stack = new ArrayDeque<>();
        MuiElement root = null;
        int nodes = 0;
        try {
            XMLInputFactory factory = secureFactory();
            reader = factory.createXMLStreamReader(new StringReader(source));
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if (stack.size() >= limits.maxDepth) fail(reader, "XML nesting exceeds the configured depth limit");
                        if (++nodes > limits.maxNodes) fail(reader, "XML node count exceeds the configured limit");
                        MuiElement element = document.createElement(qualifiedName(reader.getPrefix(), reader.getLocalName()));
                        if (reader.getAttributeCount() > limits.maxAttributesPerElement) {
                            fail(reader, "XML attribute count exceeds the configured limit");
                        }
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String name = qualifiedName(reader.getAttributePrefix(i), reader.getAttributeLocalName(i));
                            element.setAttribute(name, reader.getAttributeValue(i));
                        }
                        if (stack.isEmpty()) {
                            if (root != null) fail(reader, "XML document must contain exactly one root element");
                            root = element;
                            document.appendChild(element);
                        } else {
                            stack.peek().appendChild(element);
                        }
                        stack.push(element);
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        if (stack.isEmpty()) fail(reader, "Unexpected XML end element");
                        stack.pop();
                        break;
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        if (!stack.isEmpty()) {
                            String text = reader.getText();
                            if (!text.trim().isEmpty()) {
                                if (text.length() > limits.maxTextChars) fail(reader, "XML text exceeds the configured limit");
                                if (++nodes > limits.maxNodes) fail(reader, "XML node count exceeds the configured limit");
                                MuiText textNode = document.createTextNode(text);
                                stack.peek().appendChild(textNode);
                            }
                        }
                        break;
                    case XMLStreamConstants.DTD:
                        fail(reader, "DOCTYPE/DTD is not permitted in MUI XML");
                        break;
                    default:
                        break;
                }
            }
            if (root == null || !stack.isEmpty()) throw new MuiMarkupException("XML document must contain one complete root element");
            mutation.commit();
            committed = true;
            return root;
        } catch (XMLStreamException exception) {
            throw new MuiMarkupException("Invalid XML", locationLine(reader), locationColumn(reader), exception);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // Parsing outcome is already determined.
                }
            }
            if (!committed) {
                try {
                    mutation.close();
                } catch (RuntimeException ignored) {
                    // Preserve the original parse/validation failure.
                }
            }
        }
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
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
            // JRE StAX implementations may not expose every optional hardening property.
        }
    }

    private static String qualifiedName(String prefix, String localName) {
        return prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
    }

    private static void fail(XMLStreamReader reader, String message) {
        throw new MuiMarkupException(message, locationLine(reader), locationColumn(reader), null);
    }

    private static int locationLine(XMLStreamReader reader) {
        return reader == null || reader.getLocation() == null ? -1 : reader.getLocation().getLineNumber();
    }

    private static int locationColumn(XMLStreamReader reader) {
        return reader == null || reader.getLocation() == null ? -1 : reader.getLocation().getColumnNumber();
    }

    public static final class Limits {
        private static final Limits DEFAULT = new Limits(256 * 1024, 64, 64, 10_000, 64 * 1024);
        private final int maxSourceChars;
        private final int maxDepth;
        private final int maxAttributesPerElement;
        private final int maxNodes;
        private final int maxTextChars;

        public Limits(int maxSourceChars, int maxDepth, int maxAttributesPerElement, int maxNodes, int maxTextChars) {
            this.maxSourceChars = positive(maxSourceChars, "maxSourceChars");
            this.maxDepth = positive(maxDepth, "maxDepth");
            this.maxAttributesPerElement = positive(maxAttributesPerElement, "maxAttributesPerElement");
            this.maxNodes = positive(maxNodes, "maxNodes");
            this.maxTextChars = positive(maxTextChars, "maxTextChars");
        }

        public static Limits defaults() {
            return DEFAULT;
        }

        private static int positive(int value, String name) {
            if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
            return value;
        }
    }
}

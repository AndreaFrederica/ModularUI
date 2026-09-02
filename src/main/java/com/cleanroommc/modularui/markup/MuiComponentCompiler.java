package com.cleanroommc.modularui.markup;

import com.cleanroommc.modularui.api.component.MuiComponentDescriptor;
import com.cleanroommc.modularui.api.component.MuiComponentRegistry;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.MuiText;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Expands registered XML components into an ordinary Java DOM before it is projected onto widgets. */
public final class MuiComponentCompiler {

    private static final String SLOT_TAG = "mui:slot";
    private static final String ROOT_TAG = "mui:component-root";
    private static final String NAME_ATTR = "name";
    private static final String COMPONENT_ATTR = "component";

    private final MuiComponentRegistry registry;
    private final MuiResourceResolver resolver;
    private final int maxExpansionDepth;

    public MuiComponentCompiler(MuiComponentRegistry registry, MuiResourceResolver resolver) {
        this(registry, resolver, 32);
    }

    public MuiComponentCompiler(MuiComponentRegistry registry, MuiResourceResolver resolver, int maxExpansionDepth) {
        if (maxExpansionDepth <= 0) throw new IllegalArgumentException("maxExpansionDepth must be positive");
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
        this.maxExpansionDepth = maxExpansionDepth;
    }

    /** Expands components below {@code root}; the root itself may also be a component invocation. */
    public MuiElement expand(String owner, MuiElement root) {
        if (owner == null || root == null) throw new NullPointerException();
        MuiElement rootReplacement = expandInvocation(owner, root, 0, new ArrayDeque<String>());
        if (rootReplacement != null) {
            MuiDocument document = root.getOwnerDocument();
            try (MutationScope mutation = document.beginMutation()) {
                document.removeChild(root);
                document.appendChild(rootReplacement);
                mutation.commit();
            }
            root = rootReplacement;
        }
        expandChildren(owner, root, 0, new ArrayDeque<String>());
        return root;
    }

    public void freezeRegistry() {
        this.registry.freeze();
    }

    private void expandChildren(String owner, MuiElement parent, int depth, Deque<String> stack) {
        for (MuiNode child : new ArrayList<>(parent.getChildNodes())) {
            if (!(child instanceof MuiElement)) continue;
            MuiElement element = (MuiElement) child;
            MuiElement replacement = expandInvocation(owner, element, depth, stack);
            if (replacement != null) {
                int index = parent.getChildNodes().indexOf(element);
                parent.replaceChild(replacement, element);
                element = replacement;
            }
            expandChildren(owner, element, depth, stack);
        }
    }

    private MuiElement expandInvocation(String owner, MuiElement invocation, int depth, Deque<String> stack) {
        MuiComponentDescriptor descriptor = registry.find(invocation.getTagName());
        if (descriptor == null && "mui:component".equals(invocation.getTagName())) {
            String reference = invocation.getAttribute(COMPONENT_ATTR);
            if (reference != null) descriptor = registry.find(reference);
        }
        if (descriptor == null) return null;
        if (depth >= maxExpansionDepth) throw failure("Component expansion exceeds depth limit");
        if (stack.contains(descriptor.getName())) throw failure("Recursive component reference: " + descriptor.getName());

        MuiDocument templateDocument = new MuiDocument();
        try (InputStream stream = resolver.open(owner, descriptor.getResourceId())) {
            if (stream == null) throw failure("Component resource not found: " + descriptor.getResourceId());
            String source = readUtf8(stream);
            MuiElement templateRoot = MuiXmlParser.parse(source, templateDocument);
            stack.push(descriptor.getName());
            expandChildren(owner, templateRoot, depth + 1, stack);
            stack.pop();
            return cloneTemplate(invocation.getOwnerDocument(), templateRoot, invocation);
        } catch (IOException exception) {
            throw new MuiMarkupException("Unable to read component resource " + descriptor.getResourceId(), exception);
        } finally {
            templateDocument.close();
        }
    }

    private MuiElement cloneTemplate(MuiDocument target, MuiElement source, MuiElement invocation) {
        return cloneElement(target, source, new HashMap<String, String>(invocation.getAttributes()), true, invocation);
    }

    private MuiElement cloneElement(MuiDocument target, MuiElement source, Map<String, String> props,
                                    boolean mergeInvocation, MuiElement invocation) {
        MuiElement result = target.createElement(source.getTagName());
        for (Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            result.setAttribute(attribute.getKey(), interpolate(attribute.getValue(), props));
        }
        if (mergeInvocation) {
            for (Map.Entry<String, String> attribute : props.entrySet()) {
                if (!attribute.getKey().equals(COMPONENT_ATTR) && !attribute.getKey().equals(NAME_ATTR)
                        && !"slot".equals(attribute.getKey()) && !result.hasAttribute(attribute.getKey())) {
                    result.setAttribute(attribute.getKey(), attribute.getValue());
                }
            }
        }
        for (MuiNode child : source.getChildNodes()) {
            if (child instanceof MuiElement && SLOT_TAG.equals(((MuiElement) child).getTagName())) {
                appendSlotContent(target, result, (MuiElement) child, invocation);
            } else {
                result.appendChild(cloneNode(target, child, props, invocation));
            }
        }
        if (ROOT_TAG.equals(result.getTagName())) {
            MuiElement scoped = firstElementChild(result);
            if (scoped != null) {
                if (mergeInvocation) applyInvocationAttributes(scoped, props);
                return scoped;
            }
        }
        return result;
    }

    private static void applyInvocationAttributes(MuiElement result, Map<String, String> props) {
        for (Map.Entry<String, String> attribute : props.entrySet()) {
            if (!attribute.getKey().equals(COMPONENT_ATTR) && !attribute.getKey().equals(NAME_ATTR)
                    && !"slot".equals(attribute.getKey()) && !result.hasAttribute(attribute.getKey())) {
                result.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }
    }

    private void appendSlotContent(MuiDocument target, MuiElement parent, MuiElement slot,
                                   MuiElement invocation) {
        String name = slot.getAttribute(NAME_ATTR);
        List<MuiNode> matches = new ArrayList<>();
        for (MuiNode child : invocation.getChildNodes()) {
            if (child instanceof MuiElement) {
                String assigned = ((MuiElement) child).getAttribute("slot");
                if ((name == null && assigned == null) || (name != null && name.equals(assigned))) matches.add(child);
            } else if (name == null) {
                matches.add(child);
            }
        }
        if (matches.isEmpty()) {
            for (MuiNode fallback : slot.getChildNodes()) parent.appendChild(cloneNode(target, fallback,
                    Collections.<String, String>emptyMap(), invocation));
        } else {
            for (MuiNode match : matches) parent.appendChild(cloneNode(target, match,
                    new HashMap<String, String>(invocation.getAttributes()), invocation));
        }
    }

    private MuiNode cloneNode(MuiDocument target, MuiNode source, Map<String, String> props, MuiElement invocation) {
        if (source instanceof MuiText) return target.createTextNode(interpolate(((MuiText) source).getData(), props));
        return cloneElement(target, (MuiElement) source, props, false, invocation);
    }

    private static MuiElement firstElementChild(MuiElement element) {
        for (MuiNode child : element.getChildNodes()) if (child instanceof MuiElement) return (MuiElement) child;
        return null;
    }

    private static String interpolate(String value, Map<String, String> props) {
        String result = value;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static String readUtf8(InputStream stream) throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        return result.toString();
    }

    private static MuiMarkupException failure(String message) {
        return new MuiMarkupException(message);
    }
}

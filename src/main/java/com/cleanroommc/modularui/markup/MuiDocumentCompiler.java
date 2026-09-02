package com.cleanroommc.modularui.markup;

import com.cleanroommc.modularui.api.component.MuiComponentRegistry;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.MuiText;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;

import java.util.Map;

/** Compiles XML and component templates into one target document transaction. */
public final class MuiDocumentCompiler {

    private final MuiComponentCompiler components;

    public MuiDocumentCompiler(MuiComponentRegistry registry, MuiResourceResolver resolver) {
        this.components = new MuiComponentCompiler(registry, resolver);
    }

    public MuiElement compile(String owner, String source, MuiDocument target) {
        return compile(owner, source, target, null);
    }

    /** Compiles one root and appends it to {@code parent} in the target document. */
    public MuiElement compile(String owner, String source, MuiDocument target, MuiElement parent) {
        MuiElement copy = compileDetached(owner, source, target);
        try (MutationScope mutation = target.beginMutation()) {
            if (parent == null) target.appendChild(copy);
            else parent.appendChild(copy);
            mutation.commit();
        }
        return copy;
    }

    /** Compiles one root into {@code target} without connecting it to the document tree. */
    public MuiElement compileDetached(String owner, String source, MuiDocument target) {
        if (owner == null || source == null || target == null) throw new NullPointerException();
        this.components.freezeRegistry();
        MuiDocument scratch = new MuiDocument();
        try {
            MuiElement parsed = MuiXmlParser.parse(source, scratch);
            parsed = components.expand(owner, parsed);
            return cloneNode(target, parsed);
        } finally {
            scratch.close();
        }
    }

    private static MuiElement cloneNode(MuiDocument target, MuiElement source) {
        MuiElement result = target.createElement(source.getTagName());
        for (Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            result.setAttribute(attribute.getKey(), attribute.getValue());
        }
        for (MuiNode child : source.getChildNodes()) {
            if (child instanceof MuiText) result.appendChild(target.createTextNode(((MuiText) child).getData()));
            else result.appendChild(cloneNode(target, (MuiElement) child));
        }
        return result;
    }
}

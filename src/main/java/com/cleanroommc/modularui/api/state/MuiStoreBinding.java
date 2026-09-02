package com.cleanroommc.modularui.api.state;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.MuiText;

import java.util.Objects;

/** Small DOM binding helpers for Java controllers and XML-only screens. */
public final class MuiStoreBinding {

    private MuiStoreBinding() {}

    public static StoreSubscription attribute(MuiStore store, String key, MuiElement element, String attribute) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(attribute, "attribute");
        String normalized = key.trim();
        updateAttribute(store, normalized, element, attribute);
        return store.subscribe(change -> {
            if (change.getChangedKeys().contains(normalized) && element.isAlive()) {
                updateAttribute(store, normalized, element, attribute);
            }
        });
    }

    public static StoreSubscription text(MuiStore store, String key, MuiElement element) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(element, "element");
        String normalized = key.trim();
        updateText(store, normalized, element);
        return store.subscribe(change -> {
            if (change.getChangedKeys().contains(normalized) && element.isAlive()) {
                updateText(store, normalized, element);
            }
        });
    }

    private static void updateAttribute(MuiStore store, String key, MuiElement element, String attribute) {
        Object value = store.get(key);
        if (value == null) element.removeAttribute(attribute);
        else element.setAttribute(attribute, String.valueOf(value));
    }

    private static void updateText(MuiStore store, String key, MuiElement element) {
        Object raw = store.get(key);
        String value = raw == null ? "" : String.valueOf(raw);
        for (MuiNode node : element.getChildNodes()) {
            if (node instanceof MuiText) {
                ((MuiText) node).setData(value);
                return;
            }
        }
        element.setAttribute("text", value);
    }
}

package com.cleanroommc.modularui.api.event;

import com.cleanroommc.modularui.api.dom.MuiElement;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Document-local allowlist of Java handlers addressable from XML event attributes. */
public final class MuiActionRegistry {

    private final Map<String, MuiActionHandler> handlers = new LinkedHashMap<>();

    public MuiActionRegistration register(String name, MuiActionHandler handler) {
        name = normalizeName(name);
        Objects.requireNonNull(handler, "handler");
        if (this.handlers.putIfAbsent(name, handler) != null) {
            throw new IllegalArgumentException("Action is already registered: " + name);
        }
        return new Registration(this, name, handler);
    }

    public boolean contains(String name) {
        return this.handlers.containsKey(normalizeName(name));
    }

    public @Nullable MuiActionHandler find(String name) {
        return this.handlers.get(normalizeName(name));
    }

    /** Invokes a registered handler and returns false when the reference is currently unresolved. */
    public boolean invoke(String name, MuiElement element, MuiEvent event) {
        name = normalizeName(name);
        MuiActionHandler handler = this.handlers.get(name);
        if (handler == null) return false;
        handler.handle(new MuiActionInvocation(name, element, event));
        return true;
    }

    public void clear() {
        this.handlers.clear();
    }

    public static String normalizeName(String name) {
        String value = Objects.requireNonNull(name, "name").trim();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1
                || !validPart(value, 0, colon) || !validPart(value, colon + 1, value.length())) {
            throw new IllegalArgumentException(
                    "Action reference must be a namespaced identifier such as 'owner:save': " + value);
        }
        return value;
    }

    private static boolean validPart(String value, int start, int end) {
        for (int i = start; i < end; i++) {
            char character = value.charAt(i);
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_'
                    || character == '-' || character == '.') continue;
            return false;
        }
        return true;
    }

    private void unregister(String name, MuiActionHandler handler) {
        this.handlers.remove(name, handler);
    }

    private static final class Registration implements MuiActionRegistration {

        private final MuiActionRegistry registry;
        private final String name;
        private final MuiActionHandler handler;
        private boolean registered = true;

        private Registration(MuiActionRegistry registry, String name, MuiActionHandler handler) {
            this.registry = registry;
            this.name = name;
            this.handler = handler;
        }

        @Override
        public void unregister() {
            if (!this.registered) return;
            this.registered = false;
            this.registry.unregister(this.name, this.handler);
        }

        @Override
        public boolean isRegistered() {
            return this.registered && this.registry.handlers.get(this.name) == this.handler;
        }
    }
}

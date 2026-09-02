package com.cleanroommc.modularui.api.dom;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.event.ActionEvent;
import com.cleanroommc.modularui.api.event.EventSubscription;
import com.cleanroommc.modularui.api.event.MuiActionRegistry;
import com.cleanroommc.modularui.api.event.MuiEvent;
import com.cleanroommc.modularui.api.event.MuiEventType;
import com.cleanroommc.modularui.api.event.PointerEvent;
import com.cleanroommc.modularui.api.navigation.NavigationAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Installs DOM listeners for the safe action references stored in XML event attributes. */
final class MuiDeclarativeEventBindings {

    private final MuiActionRegistry actions;
    private final Map<MuiElement, Map<String, List<EventSubscription>>> bindings = new IdentityHashMap<>();

    MuiDeclarativeEventBindings(MuiActionRegistry actions) {
        this.actions = actions;
    }

    boolean isEventAttribute(String name) {
        return "onclick".equals(name) || "onaction".equals(name)
                || "onpointerdown".equals(name) || "onpointerup".equals(name)
                || "onpointermove".equals(name) || "onpointercancel".equals(name)
                || "onwheel".equals(name);
    }

    void validate(String name, String value) {
        if (isEventAttribute(name) && value != null) MuiActionRegistry.normalizeName(value);
    }

    void update(MuiElement element, String attribute, String value) {
        if (!isEventAttribute(attribute)) return;
        remove(element, attribute);
        if (value == null) return;
        List<EventSubscription> subscriptions = new ArrayList<>(2);
        switch (attribute) {
            case "onclick":
                subscriptions.add(element.addEventListener(PointerEvent.DOWN, event -> {
                    if (event.getButton() == 0) invoke(element, attribute, event);
                }));
                subscriptions.add(element.addEventListener(ActionEvent.ACTION, event -> {
                    if (event.getAction() == NavigationAction.ACTIVATE) invoke(element, attribute, event);
                }));
                break;
            case "onaction":
                subscriptions.add(element.addEventListener(ActionEvent.ACTION,
                        event -> invoke(element, attribute, event)));
                break;
            case "onpointerdown": bind(subscriptions, element, attribute, PointerEvent.DOWN);
                break;
            case "onpointerup": bind(subscriptions, element, attribute, PointerEvent.UP);
                break;
            case "onpointermove": bind(subscriptions, element, attribute, PointerEvent.MOVE);
                break;
            case "onpointercancel": bind(subscriptions, element, attribute, PointerEvent.CANCEL);
                break;
            case "onwheel": bind(subscriptions, element, attribute, PointerEvent.WHEEL);
                break;
            default:
                return;
        }
        this.bindings.computeIfAbsent(element, ignored -> new HashMap<>()).put(attribute, subscriptions);
    }

    private <E extends MuiEvent> void bind(List<EventSubscription> subscriptions, MuiElement element,
                                            String attribute, MuiEventType<E> type) {
        subscriptions.add(element.addEventListener(type, event -> invoke(element, attribute, event)));
    }

    private void invoke(MuiElement element, String attribute, MuiEvent event) {
        String action = element.getAttribute(attribute);
        if (action != null && !this.actions.invoke(action, element, event)) {
            ModularUI.LOGGER.warn("No Java or script action is registered for '{}' on <{} {}>",
                    action, element.getTagName(), attribute);
        }
    }

    void forget(MuiNode node) {
        if (!(node instanceof MuiElement)) return;
        Map<String, List<EventSubscription>> elementBindings = this.bindings.remove((MuiElement) node);
        if (elementBindings == null) return;
        for (List<EventSubscription> subscriptions : elementBindings.values()) close(subscriptions);
    }

    void clear() {
        for (Map<String, List<EventSubscription>> elementBindings : this.bindings.values()) {
            for (List<EventSubscription> subscriptions : elementBindings.values()) close(subscriptions);
        }
        this.bindings.clear();
    }

    private void remove(MuiElement element, String attribute) {
        Map<String, List<EventSubscription>> elementBindings = this.bindings.get(element);
        if (elementBindings == null) return;
        close(elementBindings.remove(attribute));
        if (elementBindings.isEmpty()) this.bindings.remove(element);
    }

    private static void close(List<EventSubscription> subscriptions) {
        if (subscriptions == null) return;
        for (EventSubscription subscription : subscriptions) subscription.close();
    }
}

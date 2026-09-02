package com.cleanroommc.modularui.api.event;

import com.cleanroommc.modularui.api.dom.MuiElement;

import java.util.Objects;

/** Context passed to a Java action referenced by an XML event attribute. */
public final class MuiActionInvocation {

    private final String actionName;
    private final MuiElement element;
    private final MuiEvent event;

    public MuiActionInvocation(String actionName, MuiElement element, MuiEvent event) {
        this.actionName = Objects.requireNonNull(actionName, "actionName");
        this.element = Objects.requireNonNull(element, "element");
        this.event = Objects.requireNonNull(event, "event");
    }

    public String getActionName() {
        return this.actionName;
    }

    public MuiElement getElement() {
        return this.element;
    }

    public MuiEvent getEvent() {
        return this.event;
    }
}

package com.cleanroommc.modularui.api.event;

import com.cleanroommc.modularui.api.navigation.NavigationAction;

import java.util.Objects;

public final class ActionEvent extends MuiEvent {

    public static final MuiEventType<ActionEvent> ACTION = new MuiEventType<>("action", true, true);

    private final NavigationAction action;

    public ActionEvent(NavigationAction action) {
        super(ACTION);
        this.action = Objects.requireNonNull(action, "action");
    }

    public NavigationAction getAction() {
        return this.action;
    }
}

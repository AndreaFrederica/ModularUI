package com.cleanroommc.modularui.api.event;

@FunctionalInterface
public interface MuiEventListener<E extends MuiEvent> {

    void handleEvent(E event);
}

package com.cleanroommc.modularui.api.event;

@FunctionalInterface
public interface MuiActionHandler {

    void handle(MuiActionInvocation invocation);
}

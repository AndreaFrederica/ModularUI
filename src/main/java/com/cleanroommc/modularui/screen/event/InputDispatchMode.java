package com.cleanroommc.modularui.screen.event;

public enum InputDispatchMode {
    /** Invoke only the original ModularUI interaction path. */
    LEGACY,
    /** Dispatch an event, then invoke the original behavior unless its default was prevented. */
    HYBRID,
    /** Dispatch events without invoking legacy widget behavior. */
    DOM
}

package com.cleanroommc.modularui.api.event;

/** A removable Java action registration. */
public interface MuiActionRegistration extends AutoCloseable {

    void unregister();

    boolean isRegistered();

    @Override
    default void close() {
        unregister();
    }
}

package com.cleanroommc.modularui.api.event;

/** A removable event listener registration. */
public interface EventSubscription extends AutoCloseable {

    void unsubscribe();

    boolean isSubscribed();

    @Override
    default void close() {
        unsubscribe();
    }
}

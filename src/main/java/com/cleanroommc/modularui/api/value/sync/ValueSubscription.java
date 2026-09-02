package com.cleanroommc.modularui.api.value.sync;

/** A removable listener subscription owned by a value sync handler. */
public interface ValueSubscription extends AutoCloseable {

    boolean isSubscribed();

    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}

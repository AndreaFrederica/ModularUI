package com.cleanroommc.modularui.api.state;

/** A removable subscription to a Java-side MuiStore. */
public interface StoreSubscription extends AutoCloseable {

    void unsubscribe();

    boolean isSubscribed();

    @Override
    default void close() {
        unsubscribe();
    }
}

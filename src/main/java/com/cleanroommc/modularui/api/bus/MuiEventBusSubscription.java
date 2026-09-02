package com.cleanroommc.modularui.api.bus;

public interface MuiEventBusSubscription extends AutoCloseable {

    void unsubscribe();

    boolean isSubscribed();

    @Override
    default void close() { unsubscribe(); }
}

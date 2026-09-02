package com.cleanroommc.modularui.api.sync.document;

/** Ref-counted handle for one shared remote store subscription. */
public interface RemoteStoreSubscription extends AutoCloseable {

    RemoteStore getStore();

    boolean isSubscribed();

    @Override
    void close();
}

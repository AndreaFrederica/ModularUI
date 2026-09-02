package com.cleanroommc.modularui.value.sync;

import com.cleanroommc.modularui.api.value.sync.IValueSyncHandler;
import com.cleanroommc.modularui.api.value.sync.ValueSubscription;

import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class ValueSyncHandler<T> extends SyncHandler implements IValueSyncHandler<T> {

    public static final int SYNC_VALUE = 0;

    private Runnable changeListener;
    private List<ChangeSubscription> changeSubscriptions;

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == SYNC_VALUE) read(buf);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        if (id == SYNC_VALUE) read(buf);
    }

    protected void sync() {
        sync(SYNC_VALUE, this::write);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (updateCacheFromSource(init)) sync();
    }

    /**
     * Called when the cached value of this sync handler updates. Implementations need to call this inside
     * {@link #setValue(Object, boolean, boolean)}.
     */
    protected void onValueChanged() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
        if (this.changeSubscriptions != null && !this.changeSubscriptions.isEmpty()) {
            List<ChangeSubscription> subscriptions = new ArrayList<>(this.changeSubscriptions);
            for (ChangeSubscription subscription : subscriptions) {
                if (subscription.subscribed) {
                    subscription.listener.run();
                }
            }
        }
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public Runnable getChangeListener() {
        return this.changeListener;
    }

    /**
     * Adds an independent value change listener. Closing the returned subscription removes only this listener.
     */
    public ValueSubscription addChangeListener(@NotNull Runnable changeListener) {
        Objects.requireNonNull(changeListener, "Change listener must not be null");
        if (this.changeSubscriptions == null) {
            this.changeSubscriptions = new ArrayList<>();
        }
        ChangeSubscription subscription = new ChangeSubscription(changeListener);
        this.changeSubscriptions.add(subscription);
        return subscription;
    }

    private final class ChangeSubscription implements ValueSubscription {

        private final Runnable listener;
        private boolean subscribed = true;

        private ChangeSubscription(Runnable listener) {
            this.listener = listener;
        }

        @Override
        public boolean isSubscribed() {
            return this.subscribed;
        }

        @Override
        public void unsubscribe() {
            if (!this.subscribed) return;
            this.subscribed = false;
            ValueSyncHandler.this.changeSubscriptions.remove(this);
            if (ValueSyncHandler.this.changeSubscriptions.isEmpty()) {
                ValueSyncHandler.this.changeSubscriptions = null;
            }
        }
    }
}

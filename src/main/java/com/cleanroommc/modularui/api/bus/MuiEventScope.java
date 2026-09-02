package com.cleanroommc.modularui.api.bus;

import java.util.ArrayList;
import java.util.List;

/** Owns bus subscriptions and releases them together. */
public final class MuiEventScope implements AutoCloseable {

    private final List<MuiEventBusSubscription> subscriptions = new ArrayList<>();
    private boolean closed;

    public <S extends MuiEventBusSubscription> S own(S subscription) {
        if (this.closed) {
            subscription.unsubscribe();
            throw new IllegalStateException("Event scope is closed");
        }
        this.subscriptions.add(subscription);
        return subscription;
    }

    public boolean isClosed() { return this.closed; }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        List<MuiEventBusSubscription> snapshot = new ArrayList<>(this.subscriptions);
        this.subscriptions.clear();
        for (MuiEventBusSubscription subscription : snapshot) subscription.unsubscribe();
    }
}

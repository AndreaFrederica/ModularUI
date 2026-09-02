package com.cleanroommc.modularui.api.bus;

import com.cleanroommc.modularui.ModularUI;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Thread-confined, UI-independent typed event bus. */
public final class MuiEventBus implements AutoCloseable {

    private final Thread ownerThread;
    private final Map<MuiEventTopic<?>, List<Entry<?>>> listeners = new IdentityHashMap<>();
    private long nextSequence;
    private boolean closed;

    public MuiEventBus() {
        this.ownerThread = Thread.currentThread();
    }

    public <T> MuiEventBusSubscription subscribe(MuiEventTopic<T> topic, Consumer<? super T> listener) {
        return subscribe(topic, listener, 0);
    }

    public <T> MuiEventBusSubscription subscribe(
            MuiEventTopic<T> topic, Consumer<? super T> listener, int priority) {
        checkAccess();
        ensureOpen();
        Objects.requireNonNull(topic, "topic");
        Entry<T> entry = new Entry<>(Objects.requireNonNull(listener, "listener"), priority, ++this.nextSequence);
        List<Entry<?>> entries = this.listeners.computeIfAbsent(topic, ignored -> new ArrayList<>());
        int index = 0;
        while (index < entries.size() && comesBefore(entries.get(index), entry)) index++;
        entries.add(index, entry);
        return new Subscription(topic, entry);
    }

    public <T> MuiEventBusSubscription subscribe(
            MuiEventScope scope, MuiEventTopic<T> topic, Consumer<? super T> listener, int priority) {
        return Objects.requireNonNull(scope, "scope").own(subscribe(topic, listener, priority));
    }

    public <T> void publish(MuiEventTopic<T> topic, T event) {
        checkAccess();
        ensureOpen();
        Objects.requireNonNull(topic, "topic");
        if (event != null && !topic.getEventType().isInstance(event)) {
            throw new IllegalArgumentException("Event for topic '" + topic.getId() + "' must be "
                    + topic.getEventType().getName());
        }
        List<Entry<?>> entries = this.listeners.get(topic);
        if (entries == null || entries.isEmpty()) return;
        List<Entry<?>> snapshot = new ArrayList<>(entries);
        for (Entry<?> raw : snapshot) {
            @SuppressWarnings("unchecked") Entry<T> entry = (Entry<T>) raw;
            if (!entry.active) continue;
            try { entry.listener.accept(event); }
            catch (RuntimeException exception) {
                ModularUI.LOGGER.error("Unhandled MuiEventBus listener exception for topic '{}'", topic.getId(), exception);
            }
        }
    }

    @Override
    public void close() {
        checkAccess();
        if (this.closed) return;
        this.closed = true;
        for (List<Entry<?>> entries : this.listeners.values()) {
            for (Entry<?> entry : entries) entry.active = false;
        }
        this.listeners.clear();
    }

    private static boolean comesBefore(Entry<?> existing, Entry<?> added) {
        return existing.priority > added.priority
                || (existing.priority == added.priority && existing.sequence < added.sequence);
    }

    private void checkAccess() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("MuiEventBus is confined to its creating thread");
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("MuiEventBus is closed");
    }

    private final class Subscription implements MuiEventBusSubscription {
        private final MuiEventTopic<?> topic;
        private final Entry<?> entry;

        private Subscription(MuiEventTopic<?> topic, Entry<?> entry) {
            this.topic = topic;
            this.entry = entry;
        }

        @Override
        public void unsubscribe() {
            checkAccess();
            if (!this.entry.active) return;
            this.entry.active = false;
            List<Entry<?>> entries = listeners.get(this.topic);
            if (entries != null) {
                entries.remove(this.entry);
                if (entries.isEmpty()) listeners.remove(this.topic);
            }
        }

        @Override
        public boolean isSubscribed() {
            checkAccess();
            return this.entry.active && !closed;
        }
    }

    private static final class Entry<T> {
        private final Consumer<? super T> listener;
        private final int priority;
        private final long sequence;
        private boolean active = true;

        private Entry(Consumer<? super T> listener, int priority, long sequence) {
            this.listener = listener;
            this.priority = priority;
            this.sequence = sequence;
        }
    }
}

package com.cleanroommc.modularui.screen.event;

import com.cleanroommc.modularui.api.event.EventListenerOptions;
import com.cleanroommc.modularui.api.event.EventSubscription;
import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.MuiEvent;
import com.cleanroommc.modularui.api.event.MuiEventListener;
import com.cleanroommc.modularui.api.event.MuiEventType;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Weak-identity listener storage used by the default {@link IEventTarget} methods. */
public final class EventListenerRegistry {

    private static final ReferenceQueue<IEventTarget> STALE_TARGETS = new ReferenceQueue<>();
    private static final Map<WeakIdentityKey, ListenerTable> TABLES = new HashMap<>();
    private static final Map<WeakIdentityKey, WeakReference<IEventTarget>> ALIASES = new HashMap<>();
    private static long nextListenerId;

    private EventListenerRegistry() {}

    public static synchronized <E extends MuiEvent> EventSubscription add(
            IEventTarget target, MuiEventType<E> type, MuiEventListener<? super E> listener,
            EventListenerOptions options) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(options, "options");
        purgeStaleTargets();

        target = canonicalize(target);
        WeakIdentityKey lookup = new WeakIdentityKey(target, null);
        ListenerTable table = TABLES.get(lookup);
        if (table == null) {
            table = new ListenerTable();
            TABLES.put(new WeakIdentityKey(target, STALE_TARGETS), table);
        }
        ListenerEntry<E> existing = table.find(type, listener, options.isCapture());
        if (existing != null) return new Subscription(target, type, existing.id);

        ListenerEntry<E> entry = new ListenerEntry<>(++nextListenerId, listener, options);
        table.add(type, entry);
        return new Subscription(target, type, entry.id);
    }

    public static synchronized <E extends MuiEvent> void remove(
            IEventTarget target, MuiEventType<E> type, MuiEventListener<? super E> listener,
            boolean capture) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        purgeStaleTargets();
        target = canonicalize(target);
        ListenerTable table = TABLES.get(new WeakIdentityKey(target, null));
        if (table != null) {
            table.remove(type, listener, capture);
            removeEmptyTable(target, table);
        }
    }

    public static synchronized void clear(IEventTarget target) {
        Objects.requireNonNull(target, "target");
        purgeStaleTargets();
        target = canonicalize(target);
        ListenerTable table = TABLES.remove(new WeakIdentityKey(target, null));
        if (table != null) table.clear();
    }

    public static synchronized boolean hasListeners(IEventTarget target) {
        purgeStaleTargets();
        target = canonicalize(target);
        ListenerTable table = TABLES.get(new WeakIdentityKey(target, null));
        return table != null && !table.isEmpty();
    }

    static synchronized <E extends MuiEvent> List<Invocation<E>> snapshot(
            IEventTarget target, MuiEventType<E> type, boolean capture) {
        purgeStaleTargets();
        target = canonicalize(target);
        ListenerTable table = TABLES.get(new WeakIdentityKey(target, null));
        if (table == null) return Collections.emptyList();
        List<Invocation<E>> snapshot = table.snapshot(type, capture);
        removeEmptyTable(target, table);
        return snapshot;
    }

    private static synchronized void unsubscribe(IEventTarget target, MuiEventType<?> type, long id) {
        purgeStaleTargets();
        target = canonicalize(target);
        ListenerTable table = TABLES.get(new WeakIdentityKey(target, null));
        if (table != null) {
            table.remove(type, id);
            removeEmptyTable(target, table);
        }
    }

    private static void removeEmptyTable(IEventTarget target, ListenerTable table) {
        if (table.isEmpty()) TABLES.remove(new WeakIdentityKey(target, null));
    }

    private static void purgeStaleTargets() {
        WeakIdentityKey key;
        while ((key = (WeakIdentityKey) STALE_TARGETS.poll()) != null) {
            ListenerTable table = TABLES.remove(key);
            if (table != null) table.clear();
            ALIASES.remove(key);
        }
        ALIASES.values().removeIf(reference -> reference.get() == null);
    }

    /**
     * Makes listener operations on {@code source} resolve to {@code canonical}. Existing listener tables are
     * migrated, so listeners registered before legacy Widget adoption keep working on the DOM element.
     */
    public static synchronized void alias(IEventTarget source, IEventTarget canonical) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(canonical, "canonical");
        purgeStaleTargets();
        canonical = canonicalize(canonical);
        if (source == canonical) return;
        IEventTarget previous = canonicalize(source);
        if (previous != source && previous != canonical) {
            throw new IllegalStateException("Event target is already aliased to another canonical target");
        }
        ListenerTable sourceTable = TABLES.remove(new WeakIdentityKey(source, null));
        ListenerTable canonicalTable = TABLES.get(new WeakIdentityKey(canonical, null));
        if (sourceTable != null) {
            if (canonicalTable == null) {
                canonicalTable = sourceTable;
                TABLES.put(new WeakIdentityKey(canonical, STALE_TARGETS), canonicalTable);
            } else {
                canonicalTable.merge(sourceTable);
            }
        }
        ALIASES.put(new WeakIdentityKey(source, STALE_TARGETS), new WeakReference<>(canonical));
    }

    /** Removes one adoption alias without clearing listeners stored on the canonical target. */
    public static synchronized void removeAlias(IEventTarget source, IEventTarget canonical) {
        purgeStaleTargets();
        WeakIdentityKey key = new WeakIdentityKey(Objects.requireNonNull(source, "source"), null);
        WeakReference<IEventTarget> reference = ALIASES.get(key);
        if (reference != null && reference.get() == canonical) ALIASES.remove(key);
    }

    public static synchronized IEventTarget canonicalize(IEventTarget target) {
        Objects.requireNonNull(target, "target");
        IEventTarget current = target;
        for (int depth = 0; depth < 16; depth++) {
            WeakReference<IEventTarget> reference = ALIASES.get(new WeakIdentityKey(current, null));
            if (reference == null) return current;
            IEventTarget next = reference.get();
            if (next == null) {
                ALIASES.remove(new WeakIdentityKey(current, null));
                return current;
            }
            if (next == current) return current;
            current = next;
        }
        throw new IllegalStateException("Cycle or excessive depth in event target aliases");
    }

    static final class Invocation<E extends MuiEvent> {

        final MuiEventListener<? super E> listener;
        final EventListenerOptions options;

        private Invocation(MuiEventListener<? super E> listener, EventListenerOptions options) {
            this.listener = listener;
            this.options = options;
        }
    }

    private static final class ListenerTable {

        private final Map<MuiEventType<?>, List<ListenerEntry<?>>> listeners = new HashMap<>();

        private <E extends MuiEvent> void add(MuiEventType<E> type, ListenerEntry<E> entry) {
            this.listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(entry);
        }

        @SuppressWarnings("unchecked")
        private <E extends MuiEvent> ListenerEntry<E> find(
                MuiEventType<E> type, MuiEventListener<? super E> listener, boolean capture) {
            List<ListenerEntry<?>> entries = this.listeners.get(type);
            if (entries == null) return null;
            for (ListenerEntry<?> raw : entries) {
                ListenerEntry<E> entry = (ListenerEntry<E>) raw;
                if (entry.active && entry.listener == listener && entry.options.isCapture() == capture) return entry;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private <E extends MuiEvent> List<Invocation<E>> snapshot(MuiEventType<E> type, boolean capture) {
            List<ListenerEntry<?>> entries = this.listeners.get(type);
            if (entries == null) return Collections.emptyList();
            List<Invocation<E>> result = new ArrayList<>(entries.size());
            Iterator<ListenerEntry<?>> iterator = entries.iterator();
            while (iterator.hasNext()) {
                ListenerEntry<E> entry = (ListenerEntry<E>) iterator.next();
                if (!entry.active) {
                    iterator.remove();
                } else if (entry.options.isCapture() == capture) {
                    result.add(new Invocation<>(entry.listener, entry.options));
                    if (entry.options.isOnce()) {
                        entry.active = false;
                        iterator.remove();
                    }
                }
            }
            if (entries.isEmpty()) this.listeners.remove(type);
            return result.isEmpty() ? Collections.emptyList() : result;
        }

        @SuppressWarnings("unchecked")
        private <E extends MuiEvent> void remove(
                MuiEventType<E> type, MuiEventListener<? super E> listener, boolean capture) {
            List<ListenerEntry<?>> entries = this.listeners.get(type);
            if (entries == null) return;
            Iterator<ListenerEntry<?>> iterator = entries.iterator();
            while (iterator.hasNext()) {
                ListenerEntry<E> entry = (ListenerEntry<E>) iterator.next();
                if (entry.active && entry.listener == listener && entry.options.isCapture() == capture) {
                    entry.active = false;
                    iterator.remove();
                    break;
                }
            }
            if (entries.isEmpty()) this.listeners.remove(type);
        }

        private void remove(MuiEventType<?> type, long id) {
            List<ListenerEntry<?>> entries = this.listeners.get(type);
            if (entries == null) return;
            Iterator<ListenerEntry<?>> iterator = entries.iterator();
            while (iterator.hasNext()) {
                ListenerEntry<?> entry = iterator.next();
                if (entry.id == id) {
                    entry.active = false;
                    iterator.remove();
                    break;
                }
            }
            if (entries.isEmpty()) this.listeners.remove(type);
        }

        private void clear() {
            for (List<ListenerEntry<?>> entries : this.listeners.values()) {
                for (ListenerEntry<?> entry : entries) entry.active = false;
            }
            this.listeners.clear();
        }

        private void merge(ListenerTable other) {
            for (Map.Entry<MuiEventType<?>, List<ListenerEntry<?>>> entry : other.listeners.entrySet()) {
                this.listeners.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
            }
            other.listeners.clear();
        }

        private boolean isEmpty() {
            return this.listeners.isEmpty();
        }

        private boolean contains(MuiEventType<?> type, long id) {
            List<ListenerEntry<?>> entries = this.listeners.get(type);
            if (entries == null) return false;
            for (ListenerEntry<?> entry : entries) {
                if (entry.id == id && entry.active) return true;
            }
            return false;
        }
    }

    private static final class ListenerEntry<E extends MuiEvent> {

        private final long id;
        private final MuiEventListener<? super E> listener;
        private final EventListenerOptions options;
        private boolean active = true;

        private ListenerEntry(long id, MuiEventListener<? super E> listener, EventListenerOptions options) {
            this.id = id;
            this.listener = listener;
            this.options = options;
        }
    }

    private static final class Subscription implements EventSubscription {

        private final WeakReference<IEventTarget> target;
        private final MuiEventType<?> type;
        private final long id;
        private volatile boolean subscribed = true;

        private Subscription(IEventTarget target, MuiEventType<?> type, long id) {
            this.target = new WeakReference<>(target);
            this.type = type;
            this.id = id;
        }

        @Override
        public void unsubscribe() {
            if (!this.subscribed) return;
            this.subscribed = false;
            IEventTarget eventTarget = this.target.get();
            if (eventTarget != null) EventListenerRegistry.unsubscribe(eventTarget, this.type, this.id);
        }

        @Override
        public boolean isSubscribed() {
            if (!this.subscribed) return false;
            IEventTarget eventTarget = this.target.get();
            if (eventTarget == null) {
                this.subscribed = false;
                return false;
            }
            synchronized (EventListenerRegistry.class) {
                eventTarget = canonicalize(eventTarget);
                ListenerTable table = TABLES.get(new WeakIdentityKey(eventTarget, null));
                if (table == null || !table.contains(this.type, this.id)) this.subscribed = false;
            }
            return this.subscribed;
        }
    }

    private static final class WeakIdentityKey extends WeakReference<IEventTarget> {

        private final int identityHash;

        private WeakIdentityKey(IEventTarget target, ReferenceQueue<IEventTarget> queue) {
            super(target, queue);
            this.identityHash = System.identityHashCode(target);
        }

        @Override
        public int hashCode() {
            return this.identityHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WeakIdentityKey other)) return false;
            IEventTarget target = get();
            return target != null && target == other.get();
        }
    }
}

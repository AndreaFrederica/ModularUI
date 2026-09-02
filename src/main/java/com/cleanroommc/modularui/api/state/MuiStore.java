package com.cleanroommc.modularui.api.state;

import com.cleanroommc.modularui.ModularUI;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Small Java-side reactive store for XML-only and native-controller pages.
 * It intentionally stores only JSON-like values and never serializes itself.
 */
public final class MuiStore implements AutoCloseable {

    private final Thread ownerThread;
    private final Map<String, Object> initial;
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final List<ListenerEntry> listeners = new ArrayList<>();
    private long revision;
    private boolean disposed;

    public MuiStore() {
        this(Collections.emptyMap());
    }

    public MuiStore(Map<String, ?> initialState) {
        this.ownerThread = Thread.currentThread();
        this.initial = copyState(initialState);
        this.state.putAll(copyState(initialState));
    }

    public long getRevision() {
        checkAccess();
        return this.revision;
    }

    public @Nullable Object get(String key) {
        checkAccess();
        Object value = this.state.get(normalizeKey(key));
        return value == null ? null : immutableCopy(value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) return null;
        if (!type.isInstance(value)) throw new ClassCastException("Store key '" + key + "' is not " + type.getName());
        return type.cast(value);
    }

    public boolean contains(String key) {
        checkAccess();
        return this.state.containsKey(normalizeKey(key));
    }

    public @UnmodifiableView Map<String, Object> snapshot() {
        checkAccess();
        return Collections.unmodifiableMap(copyState(this.state));
    }

    public void set(String key, @Nullable Object value) {
        String normalized = normalizeKey(key);
        if (value != null) validateValue(value, 0, new IdentityHashMap<>());
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(normalized, value);
        patch(patch);
    }

    public void patch(Map<String, ?> values) {
        checkAccess();
        ensureOpen();
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) return;
        Map<String, Object> prepared = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = normalizeKey(entry.getKey());
            Object value = entry.getValue();
            if (value != null) validateValue(value, 0, new IdentityHashMap<>());
            prepared.put(key, value == null ? null : deepCopy(value, 0, new IdentityHashMap<>()));
        }
        Map<String, Object> previous = copyState(this.state);
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : prepared.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!valueEquals(this.state.get(key), value) || !this.state.containsKey(key)) changed.add(key);
        }
        if (changed.isEmpty()) return;
        this.state.putAll(prepared);
        notifyListeners(changed, previous);
    }

    public void reset() {
        checkAccess();
        ensureOpen();
        Map<String, Object> previous = copyState(this.state);
        if (stateEquals(this.state, this.initial)) return;
        Set<String> candidates = new LinkedHashSet<>(this.state.keySet());
        candidates.addAll(this.initial.keySet());
        Set<String> changed = new LinkedHashSet<>();
        for (String key : candidates) {
            if (this.state.containsKey(key) != this.initial.containsKey(key)
                    || !valueEquals(this.state.get(key), this.initial.get(key))) changed.add(key);
        }
        this.state.clear();
        this.state.putAll(copyState(this.initial));
        notifyListeners(changed, previous);
    }

    /** Atomically replaces the complete state and emits at most one change revision. */
    public void replace(Map<String, ?> values) {
        checkAccess();
        ensureOpen();
        Map<String, Object> prepared = copyState(values);
        if (stateEquals(this.state, prepared)) return;
        Map<String, Object> previous = copyState(this.state);
        Set<String> candidates = new LinkedHashSet<>(this.state.keySet());
        candidates.addAll(prepared.keySet());
        Set<String> changed = new LinkedHashSet<>();
        for (String key : candidates) {
            if (this.state.containsKey(key) != prepared.containsKey(key)
                    || !valueEquals(this.state.get(key), prepared.get(key))) changed.add(key);
        }
        this.state.clear();
        this.state.putAll(prepared);
        notifyListeners(changed, previous);
    }

    /** Removes a key and emits one change revision when it was present. */
    public void remove(String key) {
        checkAccess();
        ensureOpen();
        String normalized = normalizeKey(key);
        if (!this.state.containsKey(normalized)) return;
        Map<String, Object> previous = copyState(this.state);
        this.state.remove(normalized);
        notifyListeners(Collections.singleton(normalized), previous);
    }

    public StoreSubscription subscribe(MuiStoreListener listener) {
        checkAccess();
        ensureOpen();
        ListenerEntry entry = new ListenerEntry(Objects.requireNonNull(listener, "listener"));
        this.listeners.add(entry);
        return new Subscription(entry);
    }

    @Override
    public void close() {
        checkAccess();
        this.disposed = true;
        this.listeners.clear();
        this.state.clear();
    }

    private void notifyListeners(Set<String> changed, Map<String, Object> previous) {
        this.revision++;
        MuiStoreChange change = new MuiStoreChange(this, this.revision, changed, previous, snapshot());
        List<ListenerEntry> snapshot = new ArrayList<>(this.listeners);
        for (ListenerEntry entry : snapshot) {
            if (!entry.active) continue;
            try { entry.listener.onChange(change); }
            catch (RuntimeException exception) {
                ModularUI.LOGGER.error("Unhandled MuiStore listener exception at revision {}", this.revision, exception);
            }
        }
    }

    private void checkAccess() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("MuiStore is confined to its creating thread");
        }
    }

    private void ensureOpen() {
        if (this.disposed) throw new IllegalStateException("MuiStore is disposed");
    }

    private static String normalizeKey(String key) {
        String value = Objects.requireNonNull(key, "key").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Store key must not be empty");
        return value;
    }

    private static Map<String, Object> copyState(Map<String, ?> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = normalizeKey(entry.getKey());
            Object value = entry.getValue();
            if (value != null) validateValue(value, 0, new IdentityHashMap<>());
            copy.put(key, value == null ? null : immutableCopy(value));
        }
        return copy;
    }

    private static Object immutableCopy(Object value) {
        return freeze(deepCopy(value, 0, new IdentityHashMap<>()));
    }

    @SuppressWarnings("unchecked")
    private static Object freeze(Object value) {
        if (value instanceof byte[]) return ((byte[]) value).clone();
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                result.put(entry.getKey(), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<Object>) value) result.add(freeze(item));
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof byte[]) return ((byte[]) value).clone();
        if (depth > 32) throw new IllegalArgumentException("Store value nesting exceeds 32 levels");
        if (seen.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Store values cannot contain cycles");
        try {
            if (value instanceof Map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("Store map keys must be strings");
                    result.put((String) entry.getKey(), deepCopy(entry.getValue(), depth + 1, seen));
                }
                return result;
            }
            if (value instanceof List) {
                List<Object> result = new ArrayList<>();
                for (Object item : (List<?>) value) result.add(deepCopy(item, depth + 1, seen));
                return result;
            }
            throw new IllegalArgumentException("Unsupported store value type: " + value.getClass().getName());
        } finally {
            seen.remove(value);
        }
    }

    private static void validateValue(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        deepCopy(value, depth, seen);
    }

    private static boolean stateEquals(Map<String, Object> first, Map<String, Object> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (String key : first.keySet()) if (!valueEquals(first.get(key), second.get(key))) return false;
        return true;
    }

    private static boolean valueEquals(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof byte[] && second instanceof byte[]) {
            return java.util.Arrays.equals((byte[]) first, (byte[]) second);
        }
        if (first instanceof List && second instanceof List) {
            List<?> left = (List<?>) first;
            List<?> right = (List<?>) second;
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!valueEquals(left.get(i), right.get(i))) return false;
            return true;
        }
        if (first instanceof Map && second instanceof Map) {
            Map<?, ?> left = (Map<?, ?>) first;
            Map<?, ?> right = (Map<?, ?>) second;
            if (!left.keySet().equals(right.keySet())) return false;
            for (Object key : left.keySet()) if (!valueEquals(left.get(key), right.get(key))) return false;
            return true;
        }
        return first.equals(second);
    }

    private final class Subscription implements StoreSubscription {
        private final ListenerEntry entry;
        private boolean subscribed = true;

        private Subscription(ListenerEntry entry) { this.entry = entry; }

        @Override
        public void unsubscribe() {
            checkAccess();
            if (!this.subscribed) return;
            this.subscribed = false;
            this.entry.active = false;
            listeners.remove(this.entry);
        }

        @Override
        public boolean isSubscribed() {
            checkAccess();
            return this.subscribed && this.entry.active && !disposed;
        }
    }

    private static final class ListenerEntry {
        private final MuiStoreListener listener;
        private boolean active = true;

        private ListenerEntry(MuiStoreListener listener) { this.listener = listener; }
    }
}

package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit wire type registry used by protocol XML; Java class names are never reflected from markup. */
public final class MuiProtocolTypeRegistry {

    private final Map<TypeKey, MuiProtocolFactory<?>> factories = new HashMap<>();
    private boolean frozen;

    public MuiProtocolTypeRegistry registerHandler(String type, int version,
                                                   MuiProtocolFactory<? extends SyncHandler> factory) {
        return register(MuiProtocolEntry.Kind.HANDLER, type, version, factory);
    }

    public MuiProtocolTypeRegistry registerSlot(String type, int version,
                                                MuiProtocolFactory<? extends ItemSlotSH> factory) {
        return register(MuiProtocolEntry.Kind.SLOT, type, version, factory);
    }

    public MuiProtocolTypeRegistry registerAction(String type, int version,
                                                  MuiProtocolFactory<? extends MuiProtocolAction> factory) {
        return register(MuiProtocolEntry.Kind.ACTION, type, version, factory);
    }

    public synchronized MuiProtocolTypeRegistry freeze() {
        this.frozen = true;
        return this;
    }

    public synchronized boolean isFrozen() { return this.frozen; }

    synchronized Object create(MuiProtocolInstallContext context, MuiProtocolEntry entry) {
        MuiProtocolFactory<?> factory = this.factories.get(new TypeKey(entry.getKind(), entry.getType(), entry.getVersion()));
        if (factory == null) {
            throw new IllegalStateException("No protocol factory registered for " + entry.getKind().name().toLowerCase()
                    + " type '" + entry.getType() + "' version " + entry.getVersion());
        }
        Object value = Objects.requireNonNull(factory.create(context, entry), "Protocol factory returned null");
        Class<?> expected = entry.getKind() == MuiProtocolEntry.Kind.ACTION ? MuiProtocolAction.class
                : entry.getKind() == MuiProtocolEntry.Kind.SLOT ? ItemSlotSH.class : SyncHandler.class;
        if (!expected.isInstance(value)) {
            throw new IllegalStateException("Protocol factory for '" + entry.getType() + "' returned "
                    + value.getClass().getName() + ", expected " + expected.getName());
        }
        return value;
    }

    private synchronized MuiProtocolTypeRegistry register(MuiProtocolEntry.Kind kind, String type, int version,
                                                          MuiProtocolFactory<?> factory) {
        if (this.frozen) throw new IllegalStateException("Protocol type registry is frozen");
        TypeKey key = new TypeKey(kind, type, version);
        MuiProtocolFactory<?> existing = this.factories.putIfAbsent(key, Objects.requireNonNull(factory, "factory"));
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate protocol type registration: " + type + '@' + version);
        }
        return this;
    }

    private static final class TypeKey {
        private final MuiProtocolEntry.Kind kind;
        private final String type;
        private final int version;

        private TypeKey(MuiProtocolEntry.Kind kind, String type, int version) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.type = normalizeType(type);
            if (version < 0) throw new IllegalArgumentException("Protocol type version must not be negative");
            this.version = version;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TypeKey)) return false;
            TypeKey other = (TypeKey) object;
            return this.kind == other.kind && this.version == other.version && this.type.equals(other.type);
        }

        @Override
        public int hashCode() { return 31 * (31 * this.kind.hashCode() + this.type.hashCode()) + this.version; }

        private static String normalizeType(String type) {
            String value = Objects.requireNonNull(type, "type").trim();
            if (value.isEmpty()) throw new IllegalArgumentException("Protocol type must not be empty");
            return value;
        }
    }
}

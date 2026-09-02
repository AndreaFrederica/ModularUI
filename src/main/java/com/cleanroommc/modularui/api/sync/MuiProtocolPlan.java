package com.cleanroommc.modularui.api.sync;

import org.jetbrains.annotations.UnmodifiableView;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable canonical protocol topology with a stable SHA-256 fingerprint. */
public final class MuiProtocolPlan {

    public static final int FORMAT_VERSION = 2;

    private final String id;
    private final int schemaVersion;
    private final List<MuiProtocolEntry> entries;
    private final byte[] canonicalBytes;
    private final byte[] fingerprint;

    private MuiProtocolPlan(String id, int schemaVersion, List<MuiProtocolEntry> entries) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.canonicalBytes = encodeCanonical();
        this.fingerprint = sha256(this.canonicalBytes);
    }

    public String getId() { return this.id; }
    public int getSchemaVersion() { return this.schemaVersion; }
    public @UnmodifiableView List<MuiProtocolEntry> getEntries() { return this.entries; }
    public byte[] getCanonicalBytes() { return this.canonicalBytes.clone(); }
    public byte[] getFingerprint() { return this.fingerprint.clone(); }

    public String getFingerprintHex() {
        StringBuilder result = new StringBuilder(this.fingerprint.length * 2);
        for (byte value : this.fingerprint) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    public boolean matches(MuiProtocolPlan other) {
        return other != null && MessageDigest.isEqual(this.fingerprint, other.fingerprint);
    }

    public static Builder builder(String id, int schemaVersion) {
        return new Builder(id, schemaVersion);
    }

    private byte[] encodeCanonical() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(FORMAT_VERSION);
            writeUtf8(output, this.id);
            output.writeInt(this.schemaVersion);
            output.writeInt(this.entries.size());
            for (MuiProtocolEntry entry : this.entries) {
                output.writeByte(entry.getKind().ordinal());
                writeUtf8(output, entry.getKey());
                writeUtf8(output, entry.getType());
                output.writeInt(entry.getVersion());
                output.writeInt(entry.getNumericId());
                output.writeInt(entry.getOrder());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode in-memory protocol plan", exception);
        }
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    public static final class Builder {
        private final String id;
        private final int schemaVersion;
        private final List<MuiProtocolEntry> entries = new ArrayList<>();
        private final Set<String> identities = new HashSet<>();
        private final Set<String> syncKeys = new HashSet<>();
        private final Set<Integer> slotOrdinals = new HashSet<>();

        private Builder(String id, int schemaVersion) {
            this.id = Objects.requireNonNull(id, "id").trim();
            if (this.id.isEmpty()) throw new IllegalArgumentException("Protocol plan id must not be empty");
            if (schemaVersion < 0) throw new IllegalArgumentException("Schema version must not be negative");
            this.schemaVersion = schemaVersion;
        }

        public Builder handler(String key, String type, int version) {
            return handler(key, 0, type, version);
        }

        public Builder handler(String key, int numericId, String type, int version) {
            return add(new MuiProtocolEntry(MuiProtocolEntry.Kind.HANDLER, key, type, version,
                    numericId, this.entries.size()));
        }

        public Builder action(String key, String type, int version) {
            return add(new MuiProtocolEntry(MuiProtocolEntry.Kind.ACTION, key, type, version, this.entries.size()));
        }

        public Builder slot(String key, String type, int version, int ordinal) {
            return slot(key, 0, type, version, ordinal);
        }

        public Builder slot(String key, int numericId, String type, int version, int ordinal) {
            MuiProtocolEntry entry = new MuiProtocolEntry(MuiProtocolEntry.Kind.SLOT, key, type, version,
                    numericId, ordinal);
            String identity = identity(entry);
            if (this.identities.contains(identity)) {
                throw new IllegalArgumentException("Duplicate protocol slot key: " + entry.getKey());
            }
            if (this.slotOrdinals.contains(ordinal)) throw new IllegalArgumentException("Duplicate slot ordinal: " + ordinal);
            addSyncKey(entry);
            this.identities.add(identity);
            this.slotOrdinals.add(ordinal);
            this.entries.add(entry);
            return this;
        }

        private Builder add(MuiProtocolEntry entry) {
            String identity = identity(entry);
            if (this.identities.contains(identity)) {
                throw new IllegalArgumentException("Duplicate protocol " + entry.getKind().name().toLowerCase() + " key: " + entry.getKey());
            }
            if (entry.getKind() != MuiProtocolEntry.Kind.ACTION) {
                String syncKey = entry.getKey() + '\0' + entry.getNumericId();
                if (this.syncKeys.contains(syncKey)) {
                    throw new IllegalArgumentException("Duplicate protocol sync key: " + entry.getKey()
                            + ':' + entry.getNumericId());
                }
                this.syncKeys.add(syncKey);
            }
            this.identities.add(identity);
            this.entries.add(entry);
            return this;
        }

        private static String identity(MuiProtocolEntry entry) {
            return entry.getKind().name() + '\0' + entry.getKey() + '\0' + entry.getNumericId();
        }

        private void addSyncKey(MuiProtocolEntry entry) {
            String syncKey = entry.getKey() + '\0' + entry.getNumericId();
            if (!this.syncKeys.add(syncKey)) {
                throw new IllegalArgumentException("Duplicate protocol sync key: " + entry.getKey()
                        + ':' + entry.getNumericId());
            }
        }

        public MuiProtocolPlan build() { return new MuiProtocolPlan(this.id, this.schemaVersion, this.entries); }
    }
}

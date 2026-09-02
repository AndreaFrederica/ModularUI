package com.cleanroommc.modularui.api.sync;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Wire-safe identity of one fixed client/server UI protocol template. */
public final class MuiTemplateContract {

    public static final int FINGERPRINT_BYTES = 32;

    private final int planFormatVersion;
    private final String schemaId;
    private final int schemaVersion;
    private final byte[] fingerprint;

    public MuiTemplateContract(int planFormatVersion, String schemaId, int schemaVersion, byte[] fingerprint) {
        if (planFormatVersion <= 0) throw new IllegalArgumentException("Plan format version must be positive");
        this.planFormatVersion = planFormatVersion;
        this.schemaId = normalizeId(schemaId);
        if (schemaVersion < 0) throw new IllegalArgumentException("Schema version must not be negative");
        this.schemaVersion = schemaVersion;
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.length != FINGERPRINT_BYTES) throw new IllegalArgumentException("Template fingerprint must contain 32 bytes");
        this.fingerprint = fingerprint.clone();
    }

    public static MuiTemplateContract fromPlan(MuiProtocolPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new MuiTemplateContract(MuiProtocolPlan.FORMAT_VERSION, plan.getId(),
                plan.getSchemaVersion(), plan.getFingerprint());
    }

    public int getPlanFormatVersion() { return this.planFormatVersion; }
    public String getSchemaId() { return this.schemaId; }
    public int getSchemaVersion() { return this.schemaVersion; }
    public byte[] getFingerprint() { return this.fingerprint.clone(); }

    public boolean matches(MuiTemplateContract other) {
        return other != null && this.planFormatVersion == other.planFormatVersion
                && this.schemaVersion == other.schemaVersion && this.schemaId.equals(other.schemaId)
                && MessageDigest.isEqual(this.fingerprint, other.fingerprint);
    }

    public String describeMismatch(MuiTemplateContract local) {
        if (local == null) return "The client does not provide the required MUI template";
        if (this.planFormatVersion != local.planFormatVersion) return "MUI protocol plan format differs";
        if (!this.schemaId.equals(local.schemaId)) return "MUI template schema id differs";
        if (this.schemaVersion != local.schemaVersion) return "MUI template schema version differs";
        if (!MessageDigest.isEqual(this.fingerprint, local.fingerprint)) return "MUI template fingerprint differs";
        return "MUI template contracts match";
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MuiTemplateContract && matches((MuiTemplateContract) object);
    }

    @Override
    public int hashCode() {
        int result = 31 * (31 * this.planFormatVersion + this.schemaId.hashCode()) + this.schemaVersion;
        return 31 * result + Arrays.hashCode(this.fingerprint);
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNull(value, "schemaId").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Template schema id must not be empty");
        if (normalized.length() > 256) throw new IllegalArgumentException("Template schema id exceeds 256 characters");
        return normalized;
    }
}

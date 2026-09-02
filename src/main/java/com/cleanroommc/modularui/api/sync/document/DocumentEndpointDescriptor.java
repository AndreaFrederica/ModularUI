package com.cleanroommc.modularui.api.sync.document;

import java.util.Objects;
import java.util.function.Predicate;

/** Immutable catalog entry containing schema, permission and parameter gates. */
public final class DocumentEndpointDescriptor {

    private final String key;
    private final int schemaVersion;
    private final Predicate<DocumentEndpointContext> permission;
    private final DocumentParameterValidator parameterValidator;
    private final DocumentEndpoint endpoint;

    private DocumentEndpointDescriptor(Builder builder) {
        this.key = builder.key;
        this.schemaVersion = builder.schemaVersion;
        this.permission = builder.permission;
        this.parameterValidator = builder.parameterValidator;
        this.endpoint = builder.endpoint;
    }

    public String getKey() { return this.key; }
    public int getSchemaVersion() { return this.schemaVersion; }

    public DocumentEndpointSubscription open(DocumentEndpointContext context, int requestedSchema,
                                             Object params, DocumentEndpointObserver observer)
            throws DocumentSyncException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(observer, "observer");
        if (requestedSchema != this.schemaVersion) {
            throw new DocumentSyncException(DocumentSyncErrorCode.SCHEMA_MISMATCH,
                    "Endpoint schema version does not match");
        }
        if (!this.permission.test(context)) {
            throw new DocumentSyncException(DocumentSyncErrorCode.FORBIDDEN, "Endpoint access denied");
        }
        this.parameterValidator.validate(params);
        DocumentEndpointSubscription subscription = this.endpoint.open(context, params, observer);
        if (subscription == null) {
            throw new DocumentSyncException(DocumentSyncErrorCode.COMMAND_FAILED,
                    "Endpoint failed to create a subscription");
        }
        return subscription;
    }

    public static Builder builder(String key, int schemaVersion, DocumentEndpoint endpoint) {
        return new Builder(key, schemaVersion, endpoint);
    }

    public static final class Builder {
        private final String key;
        private final int schemaVersion;
        private final DocumentEndpoint endpoint;
        private Predicate<DocumentEndpointContext> permission = context -> true;
        private DocumentParameterValidator parameterValidator = params -> {};

        private Builder(String key, int schemaVersion, DocumentEndpoint endpoint) {
            this.key = normalizeKey(key);
            if (schemaVersion < 0) throw new IllegalArgumentException("Schema version must not be negative");
            this.schemaVersion = schemaVersion;
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        }

        public Builder permission(Predicate<DocumentEndpointContext> permission) {
            this.permission = Objects.requireNonNull(permission, "permission");
            return this;
        }

        public Builder parameters(DocumentParameterValidator validator) {
            this.parameterValidator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        public DocumentEndpointDescriptor build() {
            return new DocumentEndpointDescriptor(this);
        }
    }

    static String normalizeKey(String key) {
        String value = Objects.requireNonNull(key, "key").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Endpoint key must not be empty");
        return value;
    }
}

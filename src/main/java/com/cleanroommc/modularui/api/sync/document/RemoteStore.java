package com.cleanroommc.modularui.api.sync.document;

import com.cleanroommc.modularui.api.state.MuiStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Client-side authoritative state projection. It never writes server state directly. */
public final class RemoteStore {

    public interface CommandSender {
        CompletableFuture<DocumentCommandResult> send(RemoteStore store, String commandKey,
                                                       long expectedRevision, Object payload);
    }

    private final String endpointKey;
    private final int schemaVersion;
    private final MuiStore state = new MuiStore();
    private final CommandSender commandSender;
    private RemoteStoreState status = RemoteStoreState.PENDING;
    private int channelId = -1;
    private long remoteRevision = -1;
    private DocumentSyncErrorCode errorCode;
    private String errorMessage;

    @ApiStatus.Internal
    public RemoteStore(String endpointKey, int schemaVersion, CommandSender commandSender) {
        this.endpointKey = DocumentEndpointDescriptor.normalizeKey(endpointKey);
        if (schemaVersion < 0) throw new IllegalArgumentException("Schema version must not be negative");
        this.schemaVersion = schemaVersion;
        this.commandSender = Objects.requireNonNull(commandSender, "commandSender");
    }

    public String getEndpointKey() { return this.endpointKey; }
    public int getSchemaVersion() { return this.schemaVersion; }
    public MuiStore getState() { return this.state; }
    public RemoteStoreState getStatus() { return this.status; }
    public boolean isActive() { return this.status == RemoteStoreState.ACTIVE; }
    public int getChannelId() { return this.channelId; }
    public long getRemoteRevision() { return this.remoteRevision; }
    public @Nullable DocumentSyncErrorCode getErrorCode() { return this.errorCode; }
    public @Nullable String getErrorMessage() { return this.errorMessage; }

    public CompletableFuture<DocumentCommandResult> command(String commandKey, Object payload) {
        return command(commandKey, this.remoteRevision, payload);
    }

    public CompletableFuture<DocumentCommandResult> command(String commandKey, long expectedRevision, Object payload) {
        if (!isActive()) {
            CompletableFuture<DocumentCommandResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Remote store is not active"));
            return future;
        }
        return this.commandSender.send(this, commandKey, expectedRevision, payload);
    }

    @ApiStatus.Internal
    public void activate(int channelId, long revision, Map<String, ?> snapshot) {
        if (this.status != RemoteStoreState.PENDING) throw new IllegalStateException("Remote store is not pending");
        if (channelId < 0 || revision < 0) throw new IllegalArgumentException("Invalid remote store identity");
        this.state.replace(snapshot);
        this.channelId = channelId;
        this.remoteRevision = revision;
        this.status = RemoteStoreState.ACTIVE;
    }

    @ApiStatus.Internal
    public void applyPatch(long baseRevision, long revision, DocumentPatch patch) {
        if (!isActive()) throw new IllegalStateException("Remote store is not active");
        if (baseRevision != this.remoteRevision || revision <= baseRevision) {
            throw new IllegalStateException("Remote store revision mismatch");
        }
        this.state.replace(patch.apply(this.state.snapshot()));
        this.remoteRevision = revision;
    }

    @ApiStatus.Internal
    public void reset(long revision, Map<String, ?> snapshot) {
        if (!isActive() || revision < this.remoteRevision) throw new IllegalStateException("Remote store reset revision mismatch");
        this.state.replace(snapshot);
        this.remoteRevision = revision;
    }

    @ApiStatus.Internal
    public void fail(DocumentSyncErrorCode code, String message) {
        if (this.status == RemoteStoreState.CLOSED) return;
        this.status = RemoteStoreState.ERROR;
        this.errorCode = Objects.requireNonNull(code, "code");
        this.errorMessage = Objects.requireNonNull(message, "message");
        this.channelId = -1;
    }

    @ApiStatus.Internal
    public void close() {
        if (this.status == RemoteStoreState.CLOSED) return;
        this.status = RemoteStoreState.CLOSED;
        this.channelId = -1;
        this.state.close();
    }
}

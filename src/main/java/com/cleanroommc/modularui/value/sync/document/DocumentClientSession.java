package com.cleanroommc.modularui.value.sync.document;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.MuiValueCodec;
import com.cleanroommc.modularui.api.sync.document.DocumentCommandResult;
import com.cleanroommc.modularui.api.sync.document.DocumentPatch;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncErrorCode;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncException;
import com.cleanroommc.modularui.api.sync.document.RemoteStore;
import com.cleanroommc.modularui.api.sync.document.RemoteStoreSubscription;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Network-independent client state machine for one fixed document sync handler. */
public final class DocumentClientSession implements AutoCloseable, RemoteStore.CommandSender {

    public static final int PROTOCOL_VERSION = 1;

    private final Thread ownerThread = Thread.currentThread();
    private final DocumentSyncLimits localLimits;
    private final Map<SubscriptionKey, SharedSubscription> shared = new LinkedHashMap<>();
    private final Map<Integer, SharedSubscription> pendingSubscriptions = new HashMap<>();
    private final Map<Integer, SharedSubscription> channels = new HashMap<>();
    private final Map<Integer, PendingCommand> pendingCommands = new HashMap<>();
    private final Set<Integer> cancelledSubscriptions = new LinkedHashSet<>();
    private DocumentFrameSink sink;
    private DocumentSyncLimits effectiveLimits;
    private State state = State.NEW;
    private int nextRequestId = 1;

    public DocumentClientSession(DocumentSyncLimits limits) {
        this.localLimits = Objects.requireNonNull(limits, "limits");
        this.effectiveLimits = limits;
    }

    public void setSink(DocumentFrameSink sink) {
        checkThread();
        if (this.state != State.NEW || this.sink != null) throw new IllegalStateException("Document client sink is already configured");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public void start() {
        checkThread();
        ensureOpen();
        if (this.state != State.NEW) return;
        requireSink();
        this.state = State.HELLO_SENT;
        send(DocumentFrame.hello(PROTOCOL_VERSION, 0L));
    }

    public boolean isReady() { checkThread(); return this.state == State.READY; }
    public DocumentSyncLimits getCodecLimits() { checkThread(); return this.effectiveLimits; }

    public RemoteStoreSubscription subscribe(String endpointKey, int schemaVersion, Object params) {
        checkThread();
        ensureOpen();
        if (schemaVersion < 0) throw new IllegalArgumentException("Schema version must not be negative");
        String endpoint = normalizeId(endpointKey, "endpoint key");
        Object copiedParams = copyValue(params, this.localLimits);
        SubscriptionKey key = new SubscriptionKey(endpoint, schemaVersion,
                canonical(canonicalizeMaps(copiedParams), this.localLimits));
        SharedSubscription value = this.shared.get(key);
        if (value == null) {
            RemoteStore store = new RemoteStore(endpoint, schemaVersion, this);
            value = new SharedSubscription(key, copiedParams, store);
            this.shared.put(key, value);
            if (this.state == State.READY) sendSubscribe(value);
        }
        value.references++;
        return new Handle(value);
    }

    public void receive(DocumentFrame frame) {
        checkThread();
        ensureOpen();
        Objects.requireNonNull(frame, "frame");
        switch (frame.getOpcode()) {
            case READY: receiveReady(frame); break;
            case SUBSCRIBED: receiveSubscribed(frame); break;
            case PATCH: receivePatch(frame); break;
            case RESULT: receiveResult(frame); break;
            case ERROR: receiveError(frame); break;
            case UNSUBSCRIBE: receiveUnsubscribe(frame); break;
            case RESET: receiveReset(frame); break;
            default: failProtocol("Server sent invalid frame " + frame.getOpcode());
        }
    }

    @Override
    public CompletableFuture<DocumentCommandResult> send(RemoteStore store, String commandKey,
                                                          long expectedRevision, Object payload) {
        checkThread();
        ensureReady();
        if (store == null || !store.isActive() || this.channels.get(store.getChannelId()) == null) {
            throw new IllegalStateException("Remote store is not owned by this session");
        }
        if (this.pendingCommands.size() >= this.effectiveLimits.getMaxPendingCommands()) {
            throw new IllegalStateException("Document command pending limit reached");
        }
        String command = normalizeId(commandKey, "command key");
        Object copiedPayload = copyValue(payload, this.effectiveLimits);
        int requestId = nextRequestId();
        CompletableFuture<DocumentCommandResult> future = new CompletableFuture<>();
        this.pendingCommands.put(requestId, new PendingCommand(store, future));
        try {
            send(DocumentFrame.command(store.getChannelId(), command, requestId, expectedRevision, copiedPayload));
        } catch (RuntimeException exception) {
            this.pendingCommands.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
    public void close() {
        checkThread();
        closeInternal(true);
    }

    private void closeInternal(boolean notifyServer) {
        if (this.state == State.CLOSED) return;
        if (notifyServer && this.sink != null) {
            for (Integer channelId : new ArrayList<>(this.channels.keySet())) {
                send(DocumentFrame.unsubscribe(channelId, "document closed"));
            }
        }
        DocumentSyncException closed = new DocumentSyncException(DocumentSyncErrorCode.SESSION_CLOSED,
                "Document sync session closed");
        for (PendingCommand command : this.pendingCommands.values()) command.future.completeExceptionally(closed);
        for (SharedSubscription subscription : this.shared.values()) subscription.store.close();
        this.pendingCommands.clear();
        this.pendingSubscriptions.clear();
        this.cancelledSubscriptions.clear();
        this.channels.clear();
        this.shared.clear();
        this.state = State.CLOSED;
    }

    private void receiveReady(DocumentFrame frame) {
        if (this.state != State.HELLO_SENT) failProtocol("Unexpected READY frame");
        if (frame.intValue("protocolVersion") != PROTOCOL_VERSION) failProtocol("Document protocol version mismatch");
        this.effectiveLimits = intersect(this.localLimits, DocumentFrame.decodeLimits(frame));
        this.state = State.READY;
        for (SharedSubscription subscription : new ArrayList<>(this.shared.values())) {
            if (subscription.references > 0 && subscription.requestId < 0) sendSubscribe(subscription);
        }
    }

    private void receiveSubscribed(DocumentFrame frame) {
        ensureReady();
        int requestId = positive(frame.intValue("requestId"), "request id");
        int channelId = nonNegative(frame.intValue("channelId"), "channel id");
        long revision = nonNegative(frame.longValue("revision"), "revision");
        SharedSubscription subscription = this.pendingSubscriptions.remove(requestId);
        if (subscription == null) {
            if (this.cancelledSubscriptions.remove(requestId)) {
                send(DocumentFrame.unsubscribe(channelId, "subscription was released"));
                return;
            }
            failProtocol("Unknown subscription request id");
        }
        if (this.channels.size() >= this.effectiveLimits.getMaxActiveChannels()) failProtocol("Document channel limit exceeded");
        if (this.channels.containsKey(channelId)) failProtocol("Duplicate document channel id");
        subscription.requestId = -1;
        subscription.channelId = channelId;
        subscription.store.activate(channelId, revision, frame.mapValue("snapshot"));
        this.channels.put(channelId, subscription);
    }

    private void receivePatch(DocumentFrame frame) {
        ensureReady();
        int channelId = nonNegative(frame.intValue("channelId"), "channel id");
        SharedSubscription subscription = requireChannel(channelId);
        DocumentPatch patch = frame.patch(this.effectiveLimits);
        try {
            subscription.store.applyPatch(frame.longValue("baseRevision"), frame.longValue("revision"), patch);
        } catch (IllegalStateException exception) {
            closeChannel(subscription, DocumentSyncErrorCode.REVISION_MISMATCH,
                    "Remote patch revision mismatch", true);
        }
    }

    private void receiveReset(DocumentFrame frame) {
        ensureReady();
        SharedSubscription subscription = requireChannel(nonNegative(frame.intValue("channelId"), "channel id"));
        try {
            subscription.store.reset(frame.longValue("revision"), frame.mapValue("snapshot"));
        } catch (IllegalStateException exception) {
            closeChannel(subscription, DocumentSyncErrorCode.REVISION_MISMATCH,
                    "Remote reset revision mismatch", true);
        }
    }

    private void receiveResult(DocumentFrame frame) {
        ensureReady();
        int requestId = positive(frame.intValue("requestId"), "request id");
        PendingCommand command = this.pendingCommands.remove(requestId);
        if (command == null) failProtocol("Unknown command result request id");
        long revision = nonNegative(frame.longValue("revision"), "revision");
        if (command.store.isActive() && revision < command.store.getRemoteRevision()) {
            command.future.completeExceptionally(new DocumentSyncException(DocumentSyncErrorCode.REVISION_MISMATCH,
                    "Command result revision moved backwards"));
            return;
        }
        command.future.complete(new DocumentCommandResult(revision, frame.value("payload")));
    }

    private void receiveError(DocumentFrame frame) {
        ensureReady();
        int requestId = nonNegative(frame.intValue("requestId"), "request id");
        DocumentSyncErrorCode code = frame.errorCode();
        String message = frame.stringValue("message");
        SharedSubscription subscription = this.pendingSubscriptions.remove(requestId);
        if (subscription != null) {
            subscription.requestId = -1;
            subscription.store.fail(code, message);
            return;
        }
        PendingCommand command = this.pendingCommands.remove(requestId);
        if (command != null) {
            command.future.completeExceptionally(new DocumentSyncException(code, message));
            return;
        }
        if (this.cancelledSubscriptions.remove(requestId)) return;
        if (requestId == 0) failProtocol("Server rejected document session: " + message);
        failProtocol("Unknown document error request id");
    }

    private void receiveUnsubscribe(DocumentFrame frame) {
        ensureReady();
        int channelId = nonNegative(frame.intValue("channelId"), "channel id");
        SharedSubscription subscription = this.channels.remove(channelId);
        if (subscription == null) return;
        subscription.channelId = -1;
        subscription.store.fail(DocumentSyncErrorCode.CHANNEL_NOT_FOUND, frame.stringValue("reason"));
        failCommands(subscription.store, new DocumentSyncException(DocumentSyncErrorCode.CHANNEL_NOT_FOUND,
                frame.stringValue("reason")));
    }

    private void sendSubscribe(SharedSubscription subscription) {
        if (this.pendingSubscriptions.size() + this.channels.size() >= this.effectiveLimits.getMaxActiveChannels()) {
            subscription.store.fail(DocumentSyncErrorCode.LIMIT_EXCEEDED, "Document channel limit reached");
            return;
        }
        int requestId = nextRequestId();
        subscription.requestId = requestId;
        this.pendingSubscriptions.put(requestId, subscription);
        send(DocumentFrame.subscribe(requestId, subscription.key.endpointKey,
                subscription.key.schemaVersion, subscription.params));
    }

    private void release(SharedSubscription subscription) {
        if (subscription.references <= 0) return;
        subscription.references--;
        if (subscription.references > 0) return;
        this.shared.remove(subscription.key);
        if (subscription.requestId >= 0) {
            this.pendingSubscriptions.remove(subscription.requestId);
            rememberCancelled(subscription.requestId);
        }
        if (subscription.channelId >= 0) {
            this.channels.remove(subscription.channelId);
            send(DocumentFrame.unsubscribe(subscription.channelId, "last subscriber released"));
        }
        failCommands(subscription.store, new DocumentSyncException(DocumentSyncErrorCode.SESSION_CLOSED,
                "Remote store subscription closed"));
        subscription.store.close();
    }

    private void closeChannel(SharedSubscription subscription, DocumentSyncErrorCode code,
                              String message, boolean notifyServer) {
        if (subscription.channelId >= 0) {
            this.channels.remove(subscription.channelId);
            if (notifyServer) send(DocumentFrame.unsubscribe(subscription.channelId, message));
            subscription.channelId = -1;
        }
        subscription.store.fail(code, message);
        failCommands(subscription.store, new DocumentSyncException(code, message));
    }

    private void failCommands(RemoteStore store, Throwable failure) {
        List<Integer> remove = new ArrayList<>();
        for (Map.Entry<Integer, PendingCommand> entry : this.pendingCommands.entrySet()) {
            if (entry.getValue().store == store) {
                entry.getValue().future.completeExceptionally(failure);
                remove.add(entry.getKey());
            }
        }
        for (Integer requestId : remove) this.pendingCommands.remove(requestId);
    }

    private SharedSubscription requireChannel(int channelId) {
        SharedSubscription subscription = this.channels.get(channelId);
        if (subscription == null) failProtocol("Unknown document channel id");
        return subscription;
    }

    private void rememberCancelled(int requestId) {
        this.cancelledSubscriptions.add(requestId);
        while (this.cancelledSubscriptions.size() > this.localLimits.getMaxActiveChannels()) {
            this.cancelledSubscriptions.remove(this.cancelledSubscriptions.iterator().next());
        }
    }

    private void send(DocumentFrame frame) { requireSink().send(frame); }

    private DocumentFrameSink requireSink() {
        if (this.sink == null) throw new IllegalStateException("Document client sink is not configured");
        return this.sink;
    }

    private int nextRequestId() {
        if (this.nextRequestId <= 0 || this.nextRequestId == Integer.MAX_VALUE) {
            failProtocol("Document request id space exhausted");
        }
        return this.nextRequestId++;
    }

    private void ensureReady() {
        if (this.state != State.READY) failProtocol("Document session is not ready");
    }

    private void ensureOpen() {
        if (this.state == State.CLOSED) throw new IllegalStateException("Document client session is closed");
    }

    private void failProtocol(String message) {
        closeInternal(false);
        throw new DecoderException(message);
    }

    private void checkThread() {
        if (Thread.currentThread() != this.ownerThread) throw new IllegalStateException("Document client session is thread-confined");
    }

    private static String normalizeId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }

    private static int positive(int value, String label) {
        if (value <= 0) throw new DecoderException("Invalid document " + label);
        return value;
    }

    private static int nonNegative(int value, String label) {
        if (value < 0) throw new DecoderException("Invalid document " + label);
        return value;
    }

    private static long nonNegative(long value, String label) {
        if (value < 0) throw new DecoderException("Invalid document " + label);
        return value;
    }

    private static Object copyValue(Object value, DocumentSyncLimits limits) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        MuiValueCodec.writeFrame(buffer, value, limits);
        return MuiValueCodec.readFrame(buffer, limits);
    }

    private static byte[] canonical(Object value, DocumentSyncLimits limits) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        MuiValueCodec.writeFrame(buffer, value, limits);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    private static Object canonicalizeMaps(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                sorted.put((String) entry.getKey(), canonicalizeMaps(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(canonicalizeMaps(item));
            return result;
        }
        return value;
    }

    private static DocumentSyncLimits intersect(DocumentSyncLimits first, DocumentSyncLimits second) {
        return new DocumentSyncLimits(Math.min(first.getMaxFrameBytes(), second.getMaxFrameBytes()),
                Math.min(first.getMaxStringBytes(), second.getMaxStringBytes()),
                Math.min(first.getMaxCollectionEntries(), second.getMaxCollectionEntries()),
                Math.min(first.getMaxNestingDepth(), second.getMaxNestingDepth()),
                Math.min(first.getMaxActiveChannels(), second.getMaxActiveChannels()),
                Math.min(first.getMaxPendingCommands(), second.getMaxPendingCommands()),
                Math.min(first.getMaxPatchOperations(), second.getMaxPatchOperations()));
    }

    private enum State { NEW, HELLO_SENT, READY, CLOSED }

    private static final class SubscriptionKey {
        private final String endpointKey;
        private final int schemaVersion;
        private final byte[] params;

        private SubscriptionKey(String endpointKey, int schemaVersion, byte[] params) {
            this.endpointKey = endpointKey;
            this.schemaVersion = schemaVersion;
            this.params = params;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof SubscriptionKey)) return false;
            SubscriptionKey other = (SubscriptionKey) object;
            return this.schemaVersion == other.schemaVersion && this.endpointKey.equals(other.endpointKey)
                    && Arrays.equals(this.params, other.params);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * this.endpointKey.hashCode() + this.schemaVersion) + Arrays.hashCode(this.params);
        }
    }

    private static final class SharedSubscription {
        private final SubscriptionKey key;
        private final Object params;
        private final RemoteStore store;
        private int references;
        private int requestId = -1;
        private int channelId = -1;

        private SharedSubscription(SubscriptionKey key, Object params, RemoteStore store) {
            this.key = key;
            this.params = params;
            this.store = store;
        }
    }

    private static final class PendingCommand {
        private final RemoteStore store;
        private final CompletableFuture<DocumentCommandResult> future;

        private PendingCommand(RemoteStore store, CompletableFuture<DocumentCommandResult> future) {
            this.store = store;
            this.future = future;
        }
    }

    private final class Handle implements RemoteStoreSubscription {
        private final SharedSubscription subscription;
        private boolean subscribed = true;

        private Handle(SharedSubscription subscription) { this.subscription = subscription; }

        @Override
        public RemoteStore getStore() { checkThread(); return this.subscription.store; }

        @Override
        public boolean isSubscribed() { checkThread(); return this.subscribed && state != State.CLOSED; }

        @Override
        public void close() {
            checkThread();
            if (!this.subscribed) return;
            this.subscribed = false;
            release(this.subscription);
        }
    }
}

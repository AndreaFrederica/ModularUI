package com.cleanroommc.modularui.value.sync.document;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointCatalog;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointContext;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointDescriptor;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointObserver;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointSubscription;
import com.cleanroommc.modularui.api.sync.document.DocumentPatch;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncErrorCode;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncException;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Network-independent authoritative server state machine for one document handler. */
public final class DocumentServerSession implements AutoCloseable {

    private final Thread ownerThread = Thread.currentThread();
    private final DocumentEndpointCatalog catalog;
    private final DocumentEndpointContext context;
    private final DocumentSyncLimits limits;
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final DocumentFrameSink sink;
    private State state = State.WAITING_HELLO;
    private int nextChannelId;
    private int lastRequestId;

    public DocumentServerSession(DocumentEndpointCatalog catalog, DocumentEndpointContext context,
                                 DocumentSyncLimits limits, DocumentFrameSink sink) {
        this.catalog = Objects.requireNonNull(catalog, "catalog").freeze();
        this.context = Objects.requireNonNull(context, "context");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public boolean isReady() { checkThread(); return this.state == State.READY; }
    public DocumentSyncLimits getLimits() { return this.limits; }
    public int getActiveChannelCount() { checkThread(); return this.channels.size(); }

    public void receive(DocumentFrame frame) {
        checkThread();
        ensureOpen();
        Objects.requireNonNull(frame, "frame");
        if (frame.getOpcode() == DocumentFrameOpcode.HELLO) {
            receiveHello(frame);
            return;
        }
        ensureReady();
        switch (frame.getOpcode()) {
            case SUBSCRIBE: receiveSubscribe(frame); break;
            case COMMAND: receiveCommand(frame); break;
            case UNSUBSCRIBE: receiveUnsubscribe(frame); break;
            default: rejectSession(DocumentSyncErrorCode.INVALID_REQUEST,
                    "Client sent invalid frame " + frame.getOpcode());
        }
    }

    @Override
    public void close() {
        checkThread();
        if (this.state == State.CLOSED) return;
        for (Channel channel : new ArrayList<>(this.channels.values())) closeChannel(channel);
        this.channels.clear();
        this.state = State.CLOSED;
    }

    private void receiveHello(DocumentFrame frame) {
        if (this.state != State.WAITING_HELLO) {
            rejectSession(DocumentSyncErrorCode.INVALID_REQUEST, "Duplicate document HELLO");
            return;
        }
        if (frame.intValue("protocolVersion") != DocumentClientSession.PROTOCOL_VERSION) {
            this.sink.send(DocumentFrame.error(0, DocumentSyncErrorCode.PROTOCOL_MISMATCH,
                    "Document protocol version does not match"));
            close();
            return;
        }
        this.state = State.READY;
        this.sink.send(DocumentFrame.ready(DocumentClientSession.PROTOCOL_VERSION, this.limits));
    }

    private void receiveSubscribe(DocumentFrame frame) {
        int requestId = acceptRequestId(frame.intValue("requestId"));
        if (this.channels.size() >= this.limits.getMaxActiveChannels()) {
            error(requestId, DocumentSyncErrorCode.LIMIT_EXCEEDED, "Document channel limit reached");
            return;
        }
        String endpointKey = frame.stringValue("endpointKey");
        if (endpointKey.isEmpty() || !endpointKey.equals(endpointKey.trim())) {
            error(requestId, DocumentSyncErrorCode.INVALID_REQUEST, "Invalid endpoint key");
            return;
        }
        DocumentEndpointDescriptor descriptor;
        try { descriptor = this.catalog.find(endpointKey); }
        catch (IllegalArgumentException exception) {
            error(requestId, DocumentSyncErrorCode.INVALID_REQUEST, "Invalid endpoint key");
            return;
        }
        if (descriptor == null) {
            error(requestId, DocumentSyncErrorCode.ENDPOINT_NOT_FOUND, "Document endpoint is not registered");
            return;
        }
        int channelId;
        try { channelId = nextChannelId(); }
        catch (IllegalStateException exception) {
            error(requestId, DocumentSyncErrorCode.LIMIT_EXCEEDED, "Document channel id space exhausted");
            return;
        }
        Channel channel = new Channel(channelId);
        ChannelObserver observer = new ChannelObserver(channel);
        try {
            DocumentEndpointSubscription subscription = descriptor.open(this.context,
                    frame.intValue("schemaVersion"), frame.value("params"), observer);
            long revision = subscription.getRevision();
            Map<String, ?> snapshot = subscription.snapshot();
            if (revision < 0 || snapshot == null) {
                subscription.close();
                throw new DocumentSyncException(DocumentSyncErrorCode.COMMAND_FAILED,
                        "Endpoint returned invalid initial state");
            }
            channel.subscription = subscription;
            channel.revision = revision;
            this.channels.put(channelId, channel);
            this.sink.send(DocumentFrame.subscribed(requestId, channelId, revision, snapshot));
            observer.activate();
        } catch (DocumentSyncException exception) {
            this.channels.remove(channelId);
            closeChannel(channel);
            error(requestId, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            this.channels.remove(channelId);
            closeChannel(channel);
            ModularUI.LOGGER.error("Unhandled document endpoint exception for '{}'", endpointKey, exception);
            error(requestId, DocumentSyncErrorCode.COMMAND_FAILED, "Endpoint failed to open");
        }
    }

    private void receiveCommand(DocumentFrame frame) {
        int requestId = acceptRequestId(frame.intValue("requestId"));
        int channelId = frame.intValue("channelId");
        Channel channel = this.channels.get(channelId);
        if (channel == null) {
            error(requestId, DocumentSyncErrorCode.CHANNEL_NOT_FOUND, "Document channel is not active");
            return;
        }
        long expectedRevision = frame.longValue("expectedRevision");
        if (expectedRevision >= 0 && expectedRevision != channel.revision) {
            error(requestId, DocumentSyncErrorCode.REVISION_MISMATCH, "Document state revision does not match");
            return;
        }
        String commandKey = frame.stringValue("commandKey").trim();
        if (commandKey.isEmpty()) {
            error(requestId, DocumentSyncErrorCode.INVALID_REQUEST, "Command key must not be empty");
            return;
        }
        try {
            Object result = channel.subscription.executeCommand(commandKey, frame.value("payload"));
            long actualRevision = channel.subscription.getRevision();
            if (actualRevision < channel.revision) {
                closeChannelWithNotice(channel, "Endpoint revision moved backwards");
                error(requestId, DocumentSyncErrorCode.REVISION_MISMATCH, "Endpoint revision moved backwards");
                return;
            }
            if (actualRevision > channel.revision) {
                // An endpoint that changes revision must publish PATCH/RESET before returning.
                closeChannelWithNotice(channel, "Endpoint did not publish its state change");
                error(requestId, DocumentSyncErrorCode.REVISION_MISMATCH, "Endpoint state update was not published");
                return;
            }
            this.sink.send(DocumentFrame.result(requestId, channel.revision, result));
        } catch (DocumentSyncException exception) {
            error(requestId, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            ModularUI.LOGGER.error("Unhandled document command exception for channel {}", channelId, exception);
            error(requestId, DocumentSyncErrorCode.COMMAND_FAILED, "Document command failed");
        }
    }

    private void receiveUnsubscribe(DocumentFrame frame) {
        int channelId = frame.intValue("channelId");
        if (channelId < 0) {
            rejectSession(DocumentSyncErrorCode.INVALID_REQUEST, "Invalid document channel id");
            return;
        }
        Channel channel = this.channels.remove(channelId);
        if (channel != null) closeChannel(channel);
    }

    private int acceptRequestId(int requestId) {
        if (requestId <= this.lastRequestId) {
            rejectSession(DocumentSyncErrorCode.INVALID_REQUEST, "Document request id was replayed or reordered");
        }
        this.lastRequestId = requestId;
        return requestId;
    }

    private int nextChannelId() {
        if (this.nextChannelId < 0 || this.nextChannelId == Integer.MAX_VALUE) {
            throw new IllegalStateException("Document channel id space exhausted");
        }
        while (this.channels.containsKey(this.nextChannelId)) this.nextChannelId++;
        return this.nextChannelId++;
    }

    private void error(int requestId, DocumentSyncErrorCode code, String message) {
        this.sink.send(DocumentFrame.error(requestId, code, safeMessage(message)));
    }

    private void rejectSession(DocumentSyncErrorCode code, String message) {
        DocumentFrame error = DocumentFrame.error(0, code, safeMessage(message));
        close();
        this.sink.send(error);
        throw new DecoderException(message);
    }

    private void closeChannelWithNotice(Channel channel, String reason) {
        this.channels.remove(channel.id);
        closeChannel(channel);
        this.sink.send(DocumentFrame.unsubscribe(channel.id, safeMessage(reason)));
    }

    private void closeChannel(Channel channel) {
        if (channel.closed) return;
        channel.closed = true;
        if (channel.subscription != null) {
            try { channel.subscription.close(); }
            catch (RuntimeException exception) {
                ModularUI.LOGGER.error("Unhandled exception while closing document endpoint channel {}", channel.id, exception);
            }
        }
        channel.buffered.clear();
    }

    private void ensureReady() {
        if (this.state != State.READY) rejectSession(DocumentSyncErrorCode.NOT_READY, "Document session is not ready");
    }

    private void ensureOpen() {
        if (this.state == State.CLOSED) throw new IllegalStateException("Document server session is closed");
    }

    private void checkThread() {
        if (Thread.currentThread() != this.ownerThread) throw new IllegalStateException("Document server session is thread-confined");
    }

    private static String safeMessage(String message) {
        String value = message == null ? "Document synchronization failed" : message.trim();
        if (value.isEmpty()) value = "Document synchronization failed";
        return value.length() > 512 ? value.substring(0, 512) : value;
    }

    private enum State { WAITING_HELLO, READY, CLOSED }

    private static final class Channel {
        private final int id;
        private final List<Runnable> buffered = new ArrayList<>();
        private DocumentEndpointSubscription subscription;
        private long revision = -1;
        private boolean active;
        private boolean closed;

        private Channel(int id) { this.id = id; }
    }

    private final class ChannelObserver implements DocumentEndpointObserver {
        private final Channel channel;

        private ChannelObserver(Channel channel) { this.channel = channel; }

        @Override
        public void patch(long baseRevision, long revision, DocumentPatch patch) {
            checkThread();
            Objects.requireNonNull(patch, "patch");
            if (this.channel.closed) return;
            if (!this.channel.active) {
                this.channel.buffered.add(() -> patch(baseRevision, revision, patch));
                return;
            }
            if (baseRevision != this.channel.revision || revision <= baseRevision) {
                closeChannelWithNotice(this.channel, "Endpoint patch revision mismatch");
                return;
            }
            DocumentFrame frame = DocumentFrame.patch(this.channel.id, baseRevision, revision, patch, limits);
            this.channel.revision = revision;
            emit(frame);
        }

        @Override
        public void reset(long revision, Map<String, ?> snapshot) {
            checkThread();
            Objects.requireNonNull(snapshot, "snapshot");
            if (this.channel.closed) return;
            if (!this.channel.active) {
                this.channel.buffered.add(() -> reset(revision, snapshot));
                return;
            }
            if (revision < this.channel.revision) {
                closeChannelWithNotice(this.channel, "Endpoint reset revision mismatch");
                return;
            }
            this.channel.revision = revision;
            emit(DocumentFrame.reset(this.channel.id, revision, snapshot));
        }

        private void emit(DocumentFrame frame) { sink.send(frame); }

        private void activate() {
            this.channel.active = true;
            for (Runnable update : new ArrayList<>(this.channel.buffered)) update.run();
            this.channel.buffered.clear();
        }
    }
}

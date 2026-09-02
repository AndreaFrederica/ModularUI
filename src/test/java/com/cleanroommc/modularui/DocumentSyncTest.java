package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.document.DocumentCommandResult;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointCatalog;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointContext;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointDescriptor;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointObserver;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointSubscription;
import com.cleanroommc.modularui.api.sync.document.DocumentPatch;
import com.cleanroommc.modularui.api.sync.document.DocumentPatchOperation;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncErrorCode;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncException;
import com.cleanroommc.modularui.api.sync.document.RemoteStore;
import com.cleanroommc.modularui.api.sync.document.RemoteStoreState;
import com.cleanroommc.modularui.api.sync.document.RemoteStoreSubscription;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.document.DocumentClientSession;
import com.cleanroommc.modularui.value.sync.document.DocumentFrame;
import com.cleanroommc.modularui.value.sync.document.DocumentFrameCodec;
import com.cleanroommc.modularui.value.sync.document.DocumentServerSession;
import com.cleanroommc.modularui.value.sync.document.DocumentSyncHandler;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentSyncTest {

    @Test
    void handshakeSharedSubscriptionPatchCommandAndReleaseRoundTrip() throws Exception {
        TestEndpoint endpoint = new TestEndpoint();
        DocumentEndpointCatalog catalog = new DocumentEndpointCatalog()
                .register(DocumentEndpointDescriptor.builder("test:storage", 2, endpoint)
                        .parameters(params -> {
                            if (!(params instanceof Map)) {
                                throw new DocumentSyncException(DocumentSyncErrorCode.INVALID_PARAMS,
                                        "Storage params must be a map");
                            }
                        }).build());
        Loopback loopback = new Loopback(catalog);
        loopback.start();
        assertTrue(loopback.client.isReady());
        assertTrue(loopback.server.isReady());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", 0);
        params.put("filter", "all");
        RemoteStoreSubscription first = loopback.client.subscribe("test:storage", 2, params);
        Map<String, Object> reorderedParams = new LinkedHashMap<>();
        reorderedParams.put("filter", "all");
        reorderedParams.put("page", 0);
        RemoteStoreSubscription second = loopback.client.subscribe("test:storage", 2, reorderedParams);
        RemoteStore store = first.getStore();

        assertSame(store, second.getStore());
        assertEquals(RemoteStoreState.ACTIVE, store.getStatus());
        assertEquals(1, loopback.server.getActiveChannelCount());
        assertEquals("alpha", store.getState().get("name"));
        assertEquals(0L, store.getRemoteRevision());

        endpoint.patchName("beta");
        assertEquals("beta", store.getState().get("name"));
        assertEquals(1L, store.getRemoteRevision());

        CompletableFuture<DocumentCommandResult> command = store.command("rename", "gamma");
        assertTrue(command.isDone());
        assertEquals("renamed", command.get().getPayload());
        assertEquals(2L, command.get().getRevision());
        assertEquals("gamma", store.getState().get("name"));

        first.close();
        assertTrue(second.isSubscribed());
        assertEquals(1, loopback.server.getActiveChannelCount());
        second.close();
        assertEquals(RemoteStoreState.CLOSED, store.getStatus());
        assertEquals(0, loopback.server.getActiveChannelCount());
    }

    @Test
    void endpointValidationErrorsRemainScopedToThePendingStore() {
        DocumentEndpointCatalog catalog = new DocumentEndpointCatalog().register(
                DocumentEndpointDescriptor.builder("test:private", 1, (context, params, observer) -> {
                    throw new AssertionError("permission gate must run first");
                }).permission(context -> false).build());
        Loopback loopback = new Loopback(catalog);
        loopback.start();

        RemoteStoreSubscription forbidden = loopback.client.subscribe("test:private", 1, null);
        assertEquals(RemoteStoreState.ERROR, forbidden.getStore().getStatus());
        assertEquals(DocumentSyncErrorCode.FORBIDDEN, forbidden.getStore().getErrorCode());
        assertTrue(loopback.client.isReady());

        RemoteStoreSubscription missing = loopback.client.subscribe("test:missing", 1, null);
        assertEquals(DocumentSyncErrorCode.ENDPOINT_NOT_FOUND, missing.getStore().getErrorCode());
        RemoteStoreSubscription schema = loopback.client.subscribe("test:private", 7, null);
        assertEquals(DocumentSyncErrorCode.SCHEMA_MISMATCH, schema.getStore().getErrorCode());
    }

    @Test
    void frameCodecRejectsUnknownOpcodeAndPatchBudgetAndPreservesTheNextFrame() {
        PacketBuffer unknown = new PacketBuffer(Unpooled.buffer());
        unknown.writeVarInt(1).writeByte(127);
        assertThrows(DecoderException.class,
                () -> DocumentFrameCodec.read(unknown, DocumentSyncLimits.DEFAULT));

        PacketBuffer valid = new PacketBuffer(Unpooled.buffer());
        DocumentFrameCodec.write(valid, DocumentFrame.hello(1, 0), DocumentSyncLimits.DEFAULT);
        valid.writeByte(1);
        DocumentFrameCodec.read(valid, DocumentSyncLimits.DEFAULT);
        assertEquals(1, valid.readableBytes());

        DocumentSyncLimits tiny = new DocumentSyncLimits(1024, 128, 16, 8, 4, 4, 1);
        DocumentPatch tooLarge = DocumentPatch.of(DocumentPatchOperation.set("a", 1),
                DocumentPatchOperation.set("b", 2));
        assertThrows(IllegalArgumentException.class,
                () -> DocumentFrame.patch(0, 0, 1, tooLarge, tiny));
        assertThrows(IllegalArgumentException.class, () -> DocumentPatch.of(
                DocumentPatchOperation.set("same", 1), DocumentPatchOperation.remove("same")));
    }

    @Test
    void revisionGapClosesOnlyTheAffectedRemoteStore() {
        List<DocumentFrame> outbound = new ArrayList<>();
        DocumentClientSession client = readyClient(outbound, DocumentSyncLimits.DEFAULT);
        RemoteStoreSubscription handle = client.subscribe("test:value", 1, null);
        int requestId = outbound.get(outbound.size() - 1).intValue("requestId");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("value", 1);
        client.receive(DocumentFrame.subscribed(requestId, 4, 3, snapshot));
        client.receive(DocumentFrame.patch(4, 2, 4,
                DocumentPatch.of(DocumentPatchOperation.set("value", 2)), DocumentSyncLimits.DEFAULT));

        assertEquals(RemoteStoreState.ERROR, handle.getStore().getStatus());
        assertEquals(DocumentSyncErrorCode.REVISION_MISMATCH, handle.getStore().getErrorCode());
        assertTrue(client.isReady());
        assertEquals(com.cleanroommc.modularui.value.sync.document.DocumentFrameOpcode.UNSUBSCRIBE,
                outbound.get(outbound.size() - 1).getOpcode());
    }

    @Test
    void pendingCommandLimitAndPanelHelperKeepOneFixedHandler() {
        DocumentSyncLimits limits = new DocumentSyncLimits(4096, 512, 64, 8, 4, 1, 16);
        List<DocumentFrame> outbound = new ArrayList<>();
        DocumentClientSession client = readyClient(outbound, limits);
        RemoteStoreSubscription handle = client.subscribe("test:value", 1, null);
        int requestId = outbound.get(outbound.size() - 1).intValue("requestId");
        client.receive(DocumentFrame.subscribed(requestId, 0, 0, new LinkedHashMap<>()));
        handle.getStore().command("first", null);
        assertThrows(IllegalStateException.class, () -> handle.getStore().command("second", null));

        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(true), true);
        DocumentSyncHandler first = manager.documentChannel("mui_document", null, limits);
        DocumentSyncHandler second = manager.documentChannel("mui_document", null, limits);
        assertSame(first, second);
        assertSame(first, manager.findSyncHandlerNullable("mui_document", 0));
    }

    @Test
    void serverRejectsReplayedRequestIdsAndClosesItsChannels() {
        TestEndpoint endpoint = new TestEndpoint();
        List<DocumentFrame> outbound = new ArrayList<>();
        DocumentServerSession server = new DocumentServerSession(
                new DocumentEndpointCatalog().register("test:value", 1, endpoint), context(),
                DocumentSyncLimits.DEFAULT, outbound::add);
        server.receive(DocumentFrame.hello(1, 0));
        server.receive(DocumentFrame.subscribe(1, "test:value", 1, null));
        assertEquals(1, server.getActiveChannelCount());

        assertThrows(DecoderException.class,
                () -> server.receive(DocumentFrame.command(0, "rename", 1, 0, "bad")));
        assertEquals(0, server.getActiveChannelCount());
        assertEquals(DocumentSyncErrorCode.INVALID_REQUEST,
                outbound.get(outbound.size() - 1).errorCode());
    }

    @Test
    void storeSnapshotsDetachMutableByteArrays() {
        Loopback loopback = new Loopback(new DocumentEndpointCatalog().register("test:bytes", 1,
                (context, params, observer) -> new DocumentEndpointSubscription() {
                    private final byte[] bytes = {1, 2, 3};
                    @Override public long getRevision() { return 0; }
                    @Override public Map<String, ?> snapshot() {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("bytes", this.bytes);
                        return value;
                    }
                    @Override public Object executeCommand(String commandKey, Object payload) { return null; }
                    @Override public void close() {}
                }));
        loopback.start();
        RemoteStore store = loopback.client.subscribe("test:bytes", 1, null).getStore();
        byte[] first = store.getState().get("bytes", byte[].class);
        byte[] second = store.getState().get("bytes", byte[].class);
        assertNotSame(first, second);
        first[0] = 99;
        assertEquals(1, store.getState().get("bytes", byte[].class)[0]);
    }

    private static DocumentEndpointContext context() {
        return new DocumentEndpointContext() {
            @Override public EntityPlayer getPlayer() { return null; }
            @Override public PanelSyncManager getSyncManager() { return null; }
        };
    }

    private static DocumentFrame wire(DocumentFrame frame, DocumentSyncLimits limits) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        DocumentFrameCodec.write(buffer, frame, limits);
        return DocumentFrameCodec.read(buffer, limits);
    }

    private static DocumentClientSession readyClient(List<DocumentFrame> outbound, DocumentSyncLimits limits) {
        DocumentClientSession client = new DocumentClientSession(limits);
        client.setSink(outbound::add);
        client.start();
        client.receive(DocumentFrame.ready(DocumentClientSession.PROTOCOL_VERSION, limits));
        return client;
    }

    private static final class Loopback {
        private final DocumentClientSession client = new DocumentClientSession(DocumentSyncLimits.DEFAULT);
        private final DocumentServerSession server;

        private Loopback(DocumentEndpointCatalog catalog) {
            DocumentServerSession[] serverRef = new DocumentServerSession[1];
            this.client.setSink(frame -> serverRef[0].receive(wire(frame, DocumentSyncLimits.DEFAULT)));
            this.server = new DocumentServerSession(catalog, context(), DocumentSyncLimits.DEFAULT,
                    frame -> this.client.receive(wire(frame, DocumentSyncLimits.DEFAULT)));
            serverRef[0] = this.server;
        }

        private void start() { this.client.start(); }
    }

    private static final class TestEndpoint implements com.cleanroommc.modularui.api.sync.document.DocumentEndpoint {
        private long revision;
        private String name = "alpha";
        private DocumentEndpointObserver observer;

        @Override
        public DocumentEndpointSubscription open(DocumentEndpointContext context, Object params,
                                                 DocumentEndpointObserver observer) {
            this.observer = observer;
            return new DocumentEndpointSubscription() {
                @Override public long getRevision() { return revision; }
                @Override public Map<String, ?> snapshot() {
                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("name", name);
                    state.put("items", Arrays.asList(1, 2, 3));
                    return state;
                }
                @Override public Object executeCommand(String commandKey, Object payload) throws DocumentSyncException {
                    if (!"rename".equals(commandKey)) {
                        throw new DocumentSyncException(DocumentSyncErrorCode.COMMAND_NOT_FOUND,
                                "Unknown storage command");
                    }
                    patchName((String) payload);
                    return "renamed";
                }
                @Override public void close() {}
            };
        }

        private void patchName(String newName) {
            long base = this.revision++;
            this.name = newName;
            this.observer.patch(base, this.revision,
                    DocumentPatch.of(DocumentPatchOperation.set("name", newName)));
        }
    }
}

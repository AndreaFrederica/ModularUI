package com.cleanroommc.modularui.value.sync.document;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointCatalog;
import com.cleanroommc.modularui.api.sync.document.DocumentEndpointContext;
import com.cleanroommc.modularui.api.sync.document.RemoteStoreSubscription;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.Objects;

/** One fixed PanelSyncManager handler that multiplexes all dynamic document endpoint channels. */
public abstract class DocumentSyncHandler extends SyncHandler {

    public static final int FRAME_PACKET_ID = 0;

    private final DocumentSyncLimits limits;

    private DocumentSyncHandler(DocumentSyncLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public static DocumentSyncHandler client(DocumentSyncLimits limits) {
        return new ClientHandler(limits);
    }

    public static DocumentSyncHandler server(DocumentEndpointCatalog catalog, DocumentSyncLimits limits) {
        return new ServerHandler(catalog, limits);
    }

    public DocumentSyncLimits getLimits() { return this.limits; }

    @SideOnly(Side.CLIENT)
    public RemoteStoreSubscription subscribe(String endpointKey, int schemaVersion, Object params) {
        throw new IllegalStateException("Document endpoint subscriptions are client-only");
    }

    @SideOnly(Side.CLIENT)
    public boolean isReady() { return false; }

    protected final DocumentFrame decode(int id, PacketBuffer buffer, DocumentSyncLimits codecLimits) {
        if (id != FRAME_PACKET_ID) throw new DecoderException("Unknown document sync packet id: " + id);
        DocumentFrame frame = DocumentFrameCodec.read(buffer, codecLimits);
        if (buffer.isReadable()) throw new DecoderException("Trailing bytes after document sync packet");
        return frame;
    }

    private static final class ClientHandler extends DocumentSyncHandler {

        private DocumentClientSession session;

        private ClientHandler(DocumentSyncLimits limits) {
            super(limits);
            this.session = newSession();
        }

        @Override
        public void init(String key, PanelSyncManager syncManager) {
            super.init(key, syncManager);
            if (this.session == null) this.session = newSession();
            this.session.setSink(frame -> syncToServer(FRAME_PACKET_ID,
                    buffer -> DocumentFrameCodec.write(buffer, frame, this.session.getCodecLimits())));
            this.session.start();
        }

        @Override
        public RemoteStoreSubscription subscribe(String endpointKey, int schemaVersion, Object params) {
            if (this.session == null) this.session = newSession();
            return this.session.subscribe(endpointKey, schemaVersion, params);
        }

        @Override
        public boolean isReady() { return this.session != null && this.session.isReady(); }

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {
            if (this.session == null) throw new IllegalStateException("Document client handler is not initialized");
            this.session.receive(decode(id, buf, getLimits()));
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            throw new DecoderException("Client document handler received a server-side packet");
        }

        @Override
        public void dispose() {
            try {
                if (this.session != null) this.session.close();
            } finally {
                this.session = null;
                super.dispose();
            }
        }

        private DocumentClientSession newSession() { return new DocumentClientSession(getLimits()); }
    }

    private static final class ServerHandler extends DocumentSyncHandler {

        private final DocumentEndpointCatalog catalog;
        private DocumentServerSession session;

        private ServerHandler(DocumentEndpointCatalog catalog, DocumentSyncLimits limits) {
            super(limits);
            this.catalog = Objects.requireNonNull(catalog, "catalog").freeze();
        }

        @Override
        public void init(String key, PanelSyncManager syncManager) {
            super.init(key, syncManager);
            DocumentEndpointContext context = new DocumentEndpointContext() {
                @Override
                public EntityPlayer getPlayer() { return syncManager.getPlayer(); }

                @Override
                public PanelSyncManager getSyncManager() { return syncManager; }
            };
            this.session = new DocumentServerSession(this.catalog, context, getLimits(),
                    frame -> syncToClient(FRAME_PACKET_ID,
                            buffer -> DocumentFrameCodec.write(buffer, frame, getLimits())));
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void readOnClient(int id, PacketBuffer buf) throws IOException {
            throw new DecoderException("Server document handler received a client-side packet");
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            if (this.session == null) throw new IllegalStateException("Document server handler is not initialized");
            this.session.receive(decode(id, buf, getLimits()));
        }

        @Override
        public void dispose() {
            if (this.session != null) {
                this.session.close();
                this.session = null;
            }
            super.dispose();
        }
    }
}

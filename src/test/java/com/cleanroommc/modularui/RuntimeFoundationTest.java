package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.ISyncedAction;
import com.cleanroommc.modularui.api.value.sync.ValueSubscription;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.PanelManager;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimeFoundationTest {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 240;

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void nestedSafeSectionsDeferDisposalUntilTheOutermostSectionEnds() {
        ModularScreen screen = openScreen();
        PanelManager manager = screen.getPanelManager();

        manager.doSafe(() -> {
            manager.doSafe(() -> {
                manager.closeAll();
                manager.dispose();
                assertFalse(manager.isDisposed());
                return null;
            });
            assertFalse(manager.isDisposed());
            return null;
        });

        assertTrue(manager.isDisposed());
    }

    @Test
    void safeSectionRestoresDepthAndFinishesPendingDisposalAfterException() {
        ModularScreen screen = openScreen();
        PanelManager manager = screen.getPanelManager();

        assertThrows(IllegalStateException.class, () -> manager.doSafe(() -> {
            manager.closeAll();
            manager.dispose();
            throw new IllegalStateException("expected");
        }));

        assertTrue(manager.isDisposed());
    }

    @Test
    void duplicateSyncHandlerKeysAreRejectedWithoutReplacingTheOriginal() {
        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), true);
        TestSyncHandler first = new TestSyncHandler();
        TestSyncHandler second = new TestSyncHandler();

        manager.syncValue("document", 0, first);
        manager.syncValue("document", 0, first);

        assertThrows(IllegalStateException.class, () -> manager.syncValue("document", 0, second));
        assertSame(first, manager.findSyncHandlerNullable("document", 0));
    }

    @Test
    void duplicateSyncedActionsMustBeIdenticalToBeIdempotent() {
        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), true);
        ISyncedAction first = packet -> {};

        manager.registerSyncedAction("submit", false, true, first);
        manager.registerSyncedAction("submit", false, true, first);

        assertThrows(IllegalStateException.class,
                () -> manager.registerSyncedAction("submit", false, true, packet -> {}));
        assertThrows(IllegalStateException.class,
                () -> manager.registerSyncedAction("submit", true, true, first));
    }

    @Test
    void valueChangeSubscriptionsDoNotReplaceTheLegacyListener() {
        IntSyncValue value = new IntSyncValue(() -> 0);
        AtomicInteger legacyCalls = new AtomicInteger();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        value.setChangeListener(legacyCalls::incrementAndGet);
        ValueSubscription first = value.addChangeListener(firstCalls::incrementAndGet);
        ValueSubscription second = value.addChangeListener(secondCalls::incrementAndGet);

        value.setIntValue(1, false, false);
        first.unsubscribe();
        first.unsubscribe();
        value.setIntValue(2, false, false);

        assertFalse(first.isSubscribed());
        assertTrue(second.isSubscribed());
        assertEquals(2, legacyCalls.get());
        assertEquals(1, firstCalls.get());
        assertEquals(2, secondCalls.get());
    }

    @Test
    void boundedByteBufferAcceptsPayloadAtTheLimit() {
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        encoded.writeVarInt(4);
        encoded.writeInt(0x12345678);

        ByteBuf decoded = NetworkUtils.readByteBuf(encoded, 4);
        try {
            assertEquals(4, decoded.readableBytes());
            assertEquals(0x12345678, decoded.readInt());
        } finally {
            decoded.release();
        }
    }

    @Test
    void boundedByteBufferRejectsOversizedNegativeAndTruncatedPayloads() {
        PacketBuffer oversized = new PacketBuffer(Unpooled.buffer());
        oversized.writeVarInt(5);
        assertThrows(DecoderException.class, () -> NetworkUtils.readByteBuf(oversized, 4));

        PacketBuffer negative = new PacketBuffer(Unpooled.buffer());
        negative.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> NetworkUtils.readByteBuf(negative, 4));

        PacketBuffer truncated = new PacketBuffer(Unpooled.buffer());
        truncated.writeVarInt(4);
        truncated.writeByte(1);
        assertThrows(DecoderException.class, () -> NetworkUtils.readByteBuf(truncated, 4));
    }

    @Test
    void boundedByteBufferRejectsOversizedWritesBeforeConsumingTheSource() {
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        ByteBuf source = Unpooled.buffer().writeInt(0x12345678);
        try {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtils.writeByteBuf(encoded, source, 3));
            assertEquals(0, encoded.readableBytes());
            assertEquals(4, source.readableBytes());
        } finally {
            source.release();
        }
    }

    @Test
    void boundedStringRejectsOversizedNegativeAndTruncatedPayloads() {
        PacketBuffer oversized = new PacketBuffer(Unpooled.buffer());
        oversized.writeVarInt(5);
        assertThrows(DecoderException.class, () -> NetworkUtils.readStringSafe(oversized, 4));

        PacketBuffer negative = new PacketBuffer(Unpooled.buffer());
        negative.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> NetworkUtils.readStringSafe(negative, 4));

        PacketBuffer truncated = new PacketBuffer(Unpooled.buffer());
        truncated.writeVarInt(4);
        truncated.writeByte('a');
        assertThrows(DecoderException.class, () -> NetworkUtils.readStringSafe(truncated, 4));
    }

    private static ModularScreen openScreen() {
        ModularScreen screen = new ModularScreen("runtime-foundation-test", ModularPanel.defaultPanel("main"));
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {};
        guiScreen.width = SCREEN_WIDTH;
        guiScreen.height = SCREEN_HEIGHT;
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(SCREEN_WIDTH, SCREEN_HEIGHT);
        return screen;
    }

    private static final class TestSyncHandler extends SyncHandler {

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {}
    }
}

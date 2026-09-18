package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.markup.MuiProtocolXmlParser;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.network.packets.OpenGuiPacket;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTemplateTest {

    private static final String FACTORY_NAME = "mui_protocol_test";

    @Test
    void slotRangeMatchesExplicitSlotsAndAppliesComponentOffset() {
        String range = "<slot-range prefix=\"storage.\" count=\"256\" type=\"test:item\" version=\"1\" ordinal=\"0\"/>";
        String start = "<protocol id=\"test:large-chest\" schema-version=\"2\">";
        MuiProtocolPlan compact = MuiProtocolXmlParser.parse(start + range + "</protocol>");
        StringBuilder expanded = new StringBuilder(start);
        for (int i = 0; i < 256; i++) {
            expanded.append("<slot key=\"storage.").append(i)
                    .append("\" type=\"test:item\" version=\"1\" ordinal=\"").append(i).append("\"/>");
        }
        expanded.append("</protocol>");
        assertTrue(compact.matches(MuiProtocolXmlParser.parse(expanded.toString())));
        MuiResourceResolver resolver = (owner, resource) -> new ByteArrayInputStream(
                ("<protocol-component>" + range + "</protocol-component>").getBytes(StandardCharsets.UTF_8));
        MuiProtocolPlan offset = MuiProtocolXmlParser.parse("test",
                start + "<component src=\"storage.xml\" ordinal-offset=\"36\"/></protocol>", resolver);
        assertEquals(256, offset.getEntries().size());
        assertEquals(36, offset.getEntries().get(0).getOrder());
        assertEquals(291, offset.getEntries().get(255).getOrder());
        assertEquals("storage.255", offset.getEntries().get(255).getKey());
    }

    @Test
    void slotRangesRespectExpandedLimitsAndComponentRoots() {
        String start = "<protocol id=\"test:range-limits\" schema-version=\"2\">";
        String range = "<slot-range prefix=\"storage.\" count=\"4096\" type=\"test:item\" version=\"1\" ordinal=\"0\"/>";
        assertEquals(4096, MuiProtocolXmlParser.parse(start + range + "</protocol>").getEntries().size());
        MuiResourceResolver resolver = (owner, resource) -> new ByteArrayInputStream(
                ("<protocol-component>" + range + "</protocol-component>").getBytes(StandardCharsets.UTF_8));
        String extra = "<slot key=\"extra\" type=\"test:item\" version=\"1\" ordinal=\"4096\"/>";
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse("test",
                start + extra + "<component src=\"slots.xml\"/></protocol>", resolver));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse("test",
                start + "<component src=\"slots.xml\"/>" + extra + "</protocol>", resolver));
        for (String count : new String[] { "0", "4097" }) {
            assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                    start + range.replace("count=\"4096\"", "count=\"" + count + "\"") + "</protocol>"));
        }
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                start + range.replace("ordinal=\"0\"", "ordinal=\"2147483647\"") + "</protocol>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse("test",
                start + "<component src=\"slots.xml\" ordinal-offset=\"2147483647\"/></protocol>", resolver));
        MuiResourceResolver invalidRoot = (owner, resource) -> new ByteArrayInputStream(range.getBytes(StandardCharsets.UTF_8));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse("test",
                start + "<component src=\"slots.xml\"/></protocol>", invalidRoot));
    }

    @Test
    void protocolXmlIsCanonicalAndRejectsUnsafeOrUnknownSyntax() {
        String first = "<protocol id=\"test:screen\" schema-version=\"1\">"
                + "<handler key=\"state\" id=\"4\" type=\"test:value\" version=\"1\"/>"
                + "<action key=\"refresh\" type=\"test:command\" version=\"1\"/>"
                + "<slot key=\"input\" id=\"2\" type=\"test:item\" version=\"1\" ordinal=\"0\"/>"
                + "</protocol>";
        String reordered = "<protocol schema-version=\"1\" id=\"test:screen\">\n"
                + "  <handler version=\"1\" type=\"test:value\" id=\"4\" key=\"state\" />\n"
                + "  <action version=\"1\" key=\"refresh\" type=\"test:command\" />\n"
                + "  <slot ordinal=\"0\" version=\"1\" type=\"test:item\" key=\"input\" id=\"2\" />\n"
                + "</protocol>";
        MuiProtocolPlan plan = MuiProtocolXmlParser.parse(first);
        assertTrue(plan.matches(MuiProtocolXmlParser.parse(reordered)));

        String changedId = first.replace("id=\"4\"", "id=\"5\"");
        assertFalse(plan.matches(MuiProtocolXmlParser.parse(changedId)));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<!DOCTYPE protocol [<!ENTITY x SYSTEM \"file:///tmp/x\">]><protocol id=\"x\" schema-version=\"1\"/>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"1\"><unknown key=\"x\" type=\"x\" version=\"1\"/></protocol>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"1\" extra=\"x\"/>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"nope\"/>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"1\"><handler key=\"same\" id=\"0\" type=\"x\" version=\"1\"/>"
                        + "<slot key=\"same\" id=\"0\" type=\"x\" version=\"1\" ordinal=\"0\"/></protocol>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"1\"><slot key=\"a\" type=\"x\" version=\"1\" ordinal=\"0\"/>"
                        + "<slot key=\"b\" type=\"x\" version=\"1\" ordinal=\"0\"/></protocol>"));
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(
                "<protocol id=\"x\" schema-version=\"1\"/><protocol id=\"y\" schema-version=\"1\"/>"));
    }

    @Test
    void protocolComponentsExpandBeforeCanonicalizationAndRejectRecursion() {
        String component = "<protocol-component>"
                + "<slot key=\"player\" id=\"0\" type=\"test:player\" version=\"1\" ordinal=\"0\"/>"
                + "<slot key=\"player\" id=\"1\" type=\"test:player\" version=\"1\" ordinal=\"1\"/>"
                + "</protocol-component>";
        MuiResourceResolver resolver = (owner, resource) -> "test".equals(owner) && "player.xml".equals(resource)
                ? new ByteArrayInputStream(component.getBytes(StandardCharsets.UTF_8)) : null;
        String composed = "<protocol id=\"test:component\" schema-version=\"1\">"
                + "<slot key=\"machine\" type=\"test:item\" version=\"1\" ordinal=\"0\"/>"
                + "<component src=\"player.xml\" ordinal-offset=\"1\"/>"
                + "</protocol>";
        String expanded = "<protocol id=\"test:component\" schema-version=\"1\">"
                + "<slot key=\"machine\" type=\"test:item\" version=\"1\" ordinal=\"0\"/>"
                + "<slot key=\"player\" id=\"0\" type=\"test:player\" version=\"1\" ordinal=\"1\"/>"
                + "<slot key=\"player\" id=\"1\" type=\"test:player\" version=\"1\" ordinal=\"2\"/>"
                + "</protocol>";

        MuiProtocolPlan plan = MuiProtocolXmlParser.parse("test", composed, resolver);
        assertTrue(plan.matches(MuiProtocolXmlParser.parse(expanded)));
        assertEquals(3, plan.getEntries().size());
        assertEquals(2, plan.getEntries().get(2).getOrder());
        assertThrows(MuiMarkupException.class, () -> MuiProtocolXmlParser.parse(composed));

        String recursive = "<protocol-component><component src=\"loop.xml\"/></protocol-component>";
        MuiResourceResolver recursiveResolver = (owner, resource) ->
                new ByteArrayInputStream(recursive.getBytes(StandardCharsets.UTF_8));
        String recursiveProtocol = "<protocol id=\"test:recursive\" schema-version=\"1\">"
                + "<component src=\"loop.xml\"/></protocol>";
        assertThrows(MuiMarkupException.class,
                () -> MuiProtocolXmlParser.parse("test", recursiveProtocol, recursiveResolver));
    }

    @Test
    void templateContractsCompareEveryWireIdentityField() {
        byte[] fingerprint = new byte[MuiTemplateContract.FINGERPRINT_BYTES];
        Arrays.fill(fingerprint, (byte) 7);
        MuiTemplateContract contract = new MuiTemplateContract(2, "test:screen", 3, fingerprint);
        assertTrue(contract.matches(new MuiTemplateContract(2, "test:screen", 3, fingerprint)));
        assertFalse(contract.matches(new MuiTemplateContract(1, "test:screen", 3, fingerprint)));
        assertFalse(contract.matches(new MuiTemplateContract(2, "test:other", 3, fingerprint)));
        assertFalse(contract.matches(new MuiTemplateContract(2, "test:screen", 4, fingerprint)));
        fingerprint[0] = 8;
        assertFalse(contract.matches(new MuiTemplateContract(2, "test:screen", 3, fingerprint)));

        assertNull(GuiManager.templateMismatch(null, null));
        assertEquals("The server does not declare the client's fixed MUI template",
                GuiManager.templateMismatch(null, contract));
        assertEquals("The client does not provide the required MUI template",
                GuiManager.templateMismatch(contract, null));
        assertNull(GuiManager.templateMismatch(contract,
                new MuiTemplateContract(2, "test:screen", 3, contract.getFingerprint())));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void wireRegistryIsExplicitFrozenAndTypeChecked() {
        MuiProtocolTypeRegistry registry = new MuiProtocolTypeRegistry();
        MuiProtocolFactory<TestSyncHandler> handlerFactory = (context, entry) -> new TestSyncHandler();
        registry.registerHandler("test:value", 1, handlerFactory);
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerHandler("test:value", 1, handlerFactory));

        MuiProtocolEntry handler = new MuiProtocolEntry(MuiProtocolEntry.Kind.HANDLER,
                "state", "test:value", 1, 0, 0);
        assertTrue(registry.create(null, handler) instanceof TestSyncHandler);
        assertThrows(IllegalStateException.class, () -> registry.create(null,
                new MuiProtocolEntry(MuiProtocolEntry.Kind.HANDLER, "missing", "test:missing", 1, 0, 0)));

        MuiProtocolTypeRegistry wrong = new MuiProtocolTypeRegistry();
        wrong.registerHandler("test:wrong", 1, (MuiProtocolFactory) (context, entry) -> "not a handler");
        assertThrows(IllegalStateException.class, () -> wrong.create(null,
                new MuiProtocolEntry(MuiProtocolEntry.Kind.HANDLER, "state", "test:wrong", 1, 0, 0)));

        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class,
                () -> registry.registerAction("test:command", 1,
                        (context, entry) -> new MuiProtocolAction(true, false, packet -> {})));
    }

    @Test
    void installationVerifiesHandlersActionsAndRealSlotTopology() {
        MuiProtocolPlan plan = MuiProtocolPlan.builder("test:install", 1)
                .handler("state", 3, "test:value", 1)
                .action("refresh", "test:command", 1)
                .slot("input", 8, "test:item", 1, 0)
                .slot("output", 9, "test:item", 1, 1)
                .build();
        MuiProtocolInstallation installation = new MuiProtocolInstallation(plan);
        PanelSyncManager syncManager = new PanelSyncManager(new ModularSyncManager(false), true);
        ItemStackHandler inventory = new ItemStackHandler(2);
        TestSyncHandler handler = new TestSyncHandler();
        MuiProtocolAction action = new MuiProtocolAction(false, true, packet -> {});
        ItemSlotSH input = new ItemSlotSH(new ModularSlot(inventory, 0));
        ItemSlotSH output = new ItemSlotSH(new ModularSlot(inventory, 1));

        for (MuiProtocolEntry entry : plan.getEntries()) {
            switch (entry.getKind()) {
                case HANDLER:
                    installation.put(entry, handler);
                    syncManager.syncValue(entry.getKey(), entry.getNumericId(), handler);
                    break;
                case ACTION:
                    installation.put(entry, action);
                    syncManager.registerSyncedAction(entry.getKey(), action.isExecuteClient(),
                            action.isExecuteServer(), action.getAction());
                    break;
                case SLOT:
                    ItemSlotSH slot = entry.getOrder() == 0 ? input : output;
                    installation.put(entry, slot);
                    syncManager.syncValue(entry.getKey(), entry.getNumericId(), slot);
                    break;
            }
        }
        installation.complete();
        syncManager.verifyProtocolInstallation(installation);
        assertSame(handler, installation.getHandler("state", 3));
        assertSame(action, installation.getAction("refresh"));

        ModularContainer correct = new ModularContainer();
        correct.inventorySlots.add(input.getSlot());
        correct.inventorySlots.add(output.getSlot());
        installation.verifyContainerSlots(correct);

        ModularContainer reversed = new ModularContainer();
        reversed.inventorySlots.add(output.getSlot());
        reversed.inventorySlots.add(input.getSlot());
        assertThrows(IllegalStateException.class, () -> installation.verifyContainerSlots(reversed));

        syncManager.syncValue("extra", new TestSyncHandler());
        assertThrows(IllegalStateException.class, () -> syncManager.verifyProtocolInstallation(installation));
        assertThrows(IllegalArgumentException.class, () -> MuiProtocolPlan.builder("test:collision", 1)
                .handler("same", 2, "test:value", 1)
                .slot("same", 2, "test:item", 1, 0));
    }

    @Test
    void openGuiPacketRoundTripsContractsAndRejectsBounds() throws IOException {
        UIFactory<GuiData> factory = protocolFactory();
        if (!GuiManager.hasFactory(FACTORY_NAME)) GuiManager.registerFactory(factory);
        byte[] fingerprint = new byte[MuiTemplateContract.FINGERPRINT_BYTES];
        Arrays.fill(fingerprint, (byte) 42);
        MuiTemplateContract contract = new MuiTemplateContract(2, "test:screen", 1, fingerprint);

        assertPacketRoundTrip(factory, null);
        assertPacketRoundTrip(factory, contract);

        PacketBuffer truncated = new PacketBuffer(Unpooled.buffer());
        truncated.writeVarInt(1).writeVarInt(2);
        NetworkUtils.writeStringSafe(truncated, FACTORY_NAME, 32, true);
        truncated.writeBoolean(true);
        truncated.writeVarInt(2);
        NetworkUtils.writeStringSafe(truncated, "test:screen", 256, true);
        truncated.writeVarInt(1).writeZero(MuiTemplateContract.FINGERPRINT_BYTES - 1);
        assertThrows(DecoderException.class, () -> new OpenGuiPacket<GuiData>().read(truncated));

        PacketBuffer tooLarge = new PacketBuffer(Unpooled.buffer((1 << 20) + 1));
        tooLarge.writeZero((1 << 20) + 1);
        OpenGuiPacket<GuiData> oversized = new OpenGuiPacket<>(1, 2, factory, tooLarge, contract);
        assertThrows(IllegalArgumentException.class,
                () -> oversized.write(new PacketBuffer(Unpooled.buffer())));
    }

    private static void assertPacketRoundTrip(UIFactory<GuiData> factory,
                                              MuiTemplateContract contract) throws IOException {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeInt(0x12345678);
        OpenGuiPacket<GuiData> original = new OpenGuiPacket<>(7, 11, factory, payload, contract);
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        original.write(encoded);
        byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(), encoded.readableBytes(), false);

        OpenGuiPacket<GuiData> decoded = new OpenGuiPacket<>();
        decoded.read(new PacketBuffer(Unpooled.wrappedBuffer(expected)));
        PacketBuffer reencoded = new PacketBuffer(Unpooled.buffer());
        decoded.write(reencoded);
        assertArrayEquals(expected,
                ByteBufUtil.getBytes(reencoded, reencoded.readerIndex(), reencoded.readableBytes(), false));
    }

    private static UIFactory<GuiData> protocolFactory() {
        return new UIFactory<GuiData>() {
            @Override
            public String getFactoryName() { return FACTORY_NAME; }

            @Override
            public ModularPanel createPanel(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
                return ModularPanel.defaultPanel("main");
            }

            @Override
            public ModularScreen createScreen(GuiData guiData, ModularPanel mainPanel) { return null; }

            @Override
            public void writeGuiData(GuiData guiData, PacketBuffer buffer) {}

            @Override
            public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) { return new GuiData(player); }
        };
    }

    private static final class TestSyncHandler extends SyncHandler {
        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {}
    }
}

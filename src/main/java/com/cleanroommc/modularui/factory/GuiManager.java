package com.cleanroommc.modularui.factory;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.MCHelper;
import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.sync.MuiProtocolInstallation;
import com.cleanroommc.modularui.api.sync.MuiProtocolTemplate;
import com.cleanroommc.modularui.api.sync.MuiTemplateContract;
import com.cleanroommc.modularui.network.ModularNetwork;
import com.cleanroommc.modularui.network.NetworkHandler;
import com.cleanroommc.modularui.network.packets.OpenGuiPacket;
import com.cleanroommc.modularui.network.packets.CloseGuiPacket;
import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class GuiManager {

    private static final Object2ObjectMap<String, UIFactory<?>> FACTORIES = new Object2ObjectOpenHashMap<>(16);

    private static final List<EntityPlayer> openedContainers = new ArrayList<>(4);

    public static void registerFactory(UIFactory<?> factory) {
        Objects.requireNonNull(factory);
        String name = Objects.requireNonNull(factory.getFactoryName());
        if (name.length() > 32) {
            throw new IllegalArgumentException("The factory name length must not exceed 32!");
        }
        if (FACTORIES.containsKey(name)) {
            throw new IllegalArgumentException("Factory with name '" + name + "' is already registered!");
        }
        FACTORIES.put(name, factory);
    }

    public static @NotNull UIFactory<?> getFactory(String name) {
        UIFactory<?> factory = FACTORIES.get(name);
        if (factory == null) throw new NoSuchElementException();
        return factory;
    }

    public static boolean hasFactory(String name) {
        return FACTORIES.containsKey(name);
    }

    public static <T extends GuiData> void open(@NotNull UIFactory<T> factory, @NotNull T guiData, EntityPlayerMP player) {
        if (player instanceof FakePlayer || openedContainers.contains(player)) return;
        openedContainers.add(player);
        // create panel, collect sync handlers and create container
        UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
        settings.defaultCanInteractWith(factory, guiData);
        ModularSyncManager msm = new ModularSyncManager(false);
        PanelSyncManager syncManager = new PanelSyncManager(msm, true);
        MuiProtocolTemplate protocolTemplate = factory.getProtocolTemplate(guiData);
        if (protocolTemplate != null) protocolTemplate.install(guiData, settings, syncManager);
        ModularPanel panel = factory.createPanel(guiData, syncManager, settings);
        finishSyncRegistration(protocolTemplate, settings, syncManager, panel);
        ModularContainer container = settings.hasCustomContainer() ? settings.createContainer() : factory.createContainer();
        container.construct(player, msm, settings, panel.getName(), guiData);
        verifyProtocolSlots(settings, container);
        // sync to client
        player.getNextWindowId();
        player.closeContainer();
        int windowId = player.currentWindowId;
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        factory.writeGuiData(guiData, buffer);
        int nid = ModularNetwork.SERVER.activate(player, msm);
        MuiTemplateContract contract = protocolTemplate == null ? null : protocolTemplate.getContract();
        NetworkHandler.sendToPlayer(new OpenGuiPacket<>(windowId, nid, factory, buffer, contract), player);
        // open container // this mimics forge behavior
        player.openContainer = container;
        player.openContainer.windowId = windowId;
        player.openContainer.addListener(player);
        // init mui syncer
        msm.onOpen();
        // finally invoke event
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, container));
    }

    @ApiStatus.Internal
    @SideOnly(Side.CLIENT)
    public static <T extends GuiData> void openFromClient(int windowId, int networkId,
                                                         @NotNull UIFactory<T> factory,
                                                         @NotNull PacketBuffer data,
                                                         @NotNull EntityPlayerSP player) {
        openFromClient(windowId, networkId, factory, data, null, player);
    }

    @ApiStatus.Internal
    @SideOnly(Side.CLIENT)
    public static <T extends GuiData> void openFromClient(int windowId, int networkId, @NotNull UIFactory<T> factory,
                                                         @NotNull PacketBuffer data, MuiTemplateContract remoteContract,
                                                         @NotNull EntityPlayerSP player) {
        T guiData = factory.readGuiData(player, data);
        MuiProtocolTemplate protocolTemplate = factory.getProtocolTemplate(guiData);
        MuiTemplateContract localContract = protocolTemplate == null ? null : protocolTemplate.getContract();
        String mismatch = templateMismatch(remoteContract, localContract);
        if (mismatch != null) {
            ModularUI.LOGGER.error("Rejected MUI '{}' because its template contract does not match: {}",
                    factory.getFactoryName(), mismatch);
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Unable to open UI: " + mismatch));
            NetworkHandler.sendToServer(new CloseGuiPacket(networkId, true));
            return;
        }
        UISettings settings = new UISettings();
        settings.defaultCanInteractWith(factory, guiData);
        ModularSyncManager msm = new ModularSyncManager(true);
        PanelSyncManager syncManager = new PanelSyncManager(msm, true);
        if (protocolTemplate != null) protocolTemplate.install(guiData, settings, syncManager);
        ModularPanel panel = factory.createPanel(guiData, syncManager, settings);
        finishSyncRegistration(protocolTemplate, settings, syncManager, panel);
        ModularScreen screen = factory.createScreen(guiData, panel);
        screen.getContext().setSettings(settings);
        ModularContainer container = settings.hasCustomContainer() ? settings.createContainer() : factory.createContainer();
        container.construct(player, msm, settings, panel.getName(), guiData);
        verifyProtocolSlots(settings, container);
        IMuiScreen wrapper = settings.hasCustomGui() ? settings.createGui(container, screen) : factory.createScreenWrapper(container, screen);
        if (!(wrapper.getGuiScreen() instanceof GuiContainer guiContainer)) {
            throw new IllegalStateException("The wrapping screen must be a GuiContainer for synced GUIs!");
        }
        if (guiContainer.inventorySlots != container) throw new IllegalStateException("Custom Containers are not yet allowed!");
        guiContainer.inventorySlots.windowId = windowId;
        ModularNetwork.CLIENT.activate(networkId, msm);
        MCHelper.displayScreen(wrapper.getGuiScreen());
        player.openContainer = guiContainer.inventorySlots;
        msm.onOpen();
    }

    @ApiStatus.Internal
    public static @org.jetbrains.annotations.Nullable String templateMismatch(
            MuiTemplateContract remote, MuiTemplateContract local) {
        if (remote == null && local == null) return null;
        if (remote == null) return "The server does not declare the client's fixed MUI template";
        if (local == null) return remote.describeMismatch(null);
        return remote.matches(local) ? null : remote.describeMismatch(local);
    }

    private static void finishSyncRegistration(MuiProtocolTemplate template, UISettings settings,
                                               PanelSyncManager syncManager, ModularPanel panel) {
        if (template == null) {
            WidgetTree.collectSyncValues(syncManager, panel);
            return;
        }
        MuiProtocolInstallation installation = Objects.requireNonNull(settings.getProtocolInstallation(),
                "Fixed template did not install its protocol");
        int unregistered = WidgetTree.countUnregisteredSyncHandlers(syncManager, panel);
        if (unregistered > 0) {
            throw new IllegalStateException("Fixed protocol UI contains " + unregistered
                    + " sync handler(s) that are not declared by its ProtocolPlan");
        }
        syncManager.verifyProtocolInstallation(installation);
    }

    private static void verifyProtocolSlots(UISettings settings, ModularContainer container) {
        MuiProtocolInstallation installation = settings.getProtocolInstallation();
        if (installation != null) installation.verifyContainerSlots(container);
    }

    @SideOnly(Side.CLIENT)
    public static <T extends GuiData> void openFromClient(@NotNull UIFactory<T> factory, @NotNull T guiData) {
        // notify server to open the gui
        // server will send packet back to actually open the gui
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        factory.writeGuiData(guiData, buffer);
        NetworkHandler.sendToServer(new OpenGuiPacket<>(0, 0, factory, buffer));
    }

    @SideOnly(Side.CLIENT)
    static void openScreen(ModularScreen screen, UISettings settings) {
        if (screen.getScreenWrapper() != null && MCHelper.getCurrentScreen() == screen.getScreenWrapper().getGuiScreen()) {
            // already open
            return;
        }
        screen.getContext().setSettings(settings);
        GuiScreen guiScreen;
        if (settings.hasCustomContainer()) {
            ModularContainer container = settings.createContainer();
            container.constructClientOnly();
            guiScreen = new GuiContainerWrapper(container, screen);
        } else {
            guiScreen = new GuiScreenWrapper(screen);
        }
        MCHelper.displayScreen(guiScreen);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            openedContainers.clear();
        }
    }
}

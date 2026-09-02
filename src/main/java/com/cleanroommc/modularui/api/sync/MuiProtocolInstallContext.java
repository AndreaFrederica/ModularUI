package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import java.util.Objects;

/** Common client/server inputs exposed to registered protocol factories. */
public final class MuiProtocolInstallContext {

    private final GuiData guiData;
    private final UISettings settings;
    private final PanelSyncManager syncManager;
    private final MuiProtocolInstallation installation;

    MuiProtocolInstallContext(GuiData guiData, UISettings settings, PanelSyncManager syncManager,
                              MuiProtocolInstallation installation) {
        this.guiData = Objects.requireNonNull(guiData, "guiData");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.syncManager = Objects.requireNonNull(syncManager, "syncManager");
        this.installation = Objects.requireNonNull(installation, "installation");
    }

    public GuiData getGuiData() { return this.guiData; }
    public <D extends GuiData> D getGuiData(Class<D> type) { return type.cast(this.guiData); }
    public UISettings getSettings() { return this.settings; }
    public PanelSyncManager getSyncManager() { return this.syncManager; }
    public MuiProtocolInstallation getInstallation() { return this.installation; }
    public boolean isClient() { return this.syncManager.isClient(); }
}

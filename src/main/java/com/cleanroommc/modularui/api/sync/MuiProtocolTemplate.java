package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import java.util.Objects;

/** Fixed common template that installs one immutable protocol plan through registered Java factories. */
public final class MuiProtocolTemplate {

    private final MuiProtocolPlan plan;
    private final MuiProtocolTypeRegistry registry;

    public MuiProtocolTemplate(MuiProtocolPlan plan, MuiProtocolTypeRegistry registry) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.registry = Objects.requireNonNull(registry, "registry").freeze();
    }

    public MuiProtocolPlan getPlan() { return this.plan; }
    public MuiTemplateContract getContract() { return MuiTemplateContract.fromPlan(this.plan); }
    public MuiProtocolTypeRegistry getRegistry() { return this.registry; }

    public MuiProtocolInstallation install(GuiData guiData, UISettings settings, PanelSyncManager syncManager) {
        Objects.requireNonNull(guiData, "guiData");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(syncManager, "syncManager");
        if (settings.getProtocolInstallation() != null) {
            throw new IllegalStateException("UISettings already contains a protocol installation");
        }
        MuiProtocolInstallation installation = new MuiProtocolInstallation(this.plan);
        MuiProtocolInstallContext context = new MuiProtocolInstallContext(guiData, settings, syncManager, installation);
        int nextSlotOrdinal = 0;
        for (MuiProtocolEntry entry : this.plan.getEntries()) {
            Object value = this.registry.create(context, entry);
            switch (entry.getKind()) {
                case HANDLER:
                    syncManager.syncValue(entry.getKey(), entry.getNumericId(), (SyncHandler) value);
                    break;
                case SLOT:
                    ItemSlotSH slot = (ItemSlotSH) value;
                    if (slot.isPhantom()) throw new IllegalStateException("Protocol slot entries must represent real container slots");
                    if (entry.getOrder() != nextSlotOrdinal++) {
                        throw new IllegalStateException("Protocol slot ordinals must be contiguous from zero");
                    }
                    syncManager.syncValue(entry.getKey(), entry.getNumericId(), slot);
                    break;
                case ACTION:
                    MuiProtocolAction action = (MuiProtocolAction) value;
                    syncManager.registerSyncedAction(entry.getKey(), action.isExecuteClient(),
                            action.isExecuteServer(), action.getAction());
                    break;
                default:
                    throw new IllegalStateException("Unsupported protocol entry kind: " + entry.getKind());
            }
            installation.put(entry, value);
        }
        installation.complete();
        settings.installProtocol(installation);
        return installation;
    }
}

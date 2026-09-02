package com.cleanroommc.modularui.api.sync.document;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import net.minecraft.entity.player.EntityPlayer;

/** Server-side context exposed to endpoint permission and command implementations. */
public interface DocumentEndpointContext {

    EntityPlayer getPlayer();

    PanelSyncManager getSyncManager();
}

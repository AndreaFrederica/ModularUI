package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.api.ISyncedAction;

import java.util.Objects;

/** Runtime action returned by an explicitly registered protocol action factory. */
public final class MuiProtocolAction {

    private final boolean executeClient;
    private final boolean executeServer;
    private final ISyncedAction action;

    public MuiProtocolAction(boolean executeClient, boolean executeServer, ISyncedAction action) {
        if (!executeClient && !executeServer) throw new IllegalArgumentException("Protocol action must execute on at least one side");
        this.executeClient = executeClient;
        this.executeServer = executeServer;
        this.action = Objects.requireNonNull(action, "action");
    }

    public boolean isExecuteClient() { return this.executeClient; }
    public boolean isExecuteServer() { return this.executeServer; }
    public ISyncedAction getAction() { return this.action; }
}

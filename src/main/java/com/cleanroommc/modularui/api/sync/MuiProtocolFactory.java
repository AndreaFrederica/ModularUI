package com.cleanroommc.modularui.api.sync;

@FunctionalInterface
public interface MuiProtocolFactory<T> {

    T create(MuiProtocolInstallContext context, MuiProtocolEntry entry);
}

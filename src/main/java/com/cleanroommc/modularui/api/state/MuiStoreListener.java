package com.cleanroommc.modularui.api.state;

@FunctionalInterface
public interface MuiStoreListener {

    void onChange(MuiStoreChange change);
}

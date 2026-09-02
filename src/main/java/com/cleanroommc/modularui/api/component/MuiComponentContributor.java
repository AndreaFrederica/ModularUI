package com.cleanroommc.modularui.api.component;

/** Extension hook used by an addon to register XML component templates before compilation starts. */
@FunctionalInterface
public interface MuiComponentContributor {
    void contribute(MuiComponentRegistry registry);
}

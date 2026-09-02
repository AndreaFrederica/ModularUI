package com.cleanroommc.modularui.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCHelperTest {

    @Test
    void detectsFormattedModNameLine() {
        assertTrue(MCHelper.containsModNameLine(
                Arrays.asList("Oak Planks", "§9§oMinecraft§r"), "Minecraft"));
    }

    @Test
    void doesNotMatchDifferentOrMissingModName() {
        assertFalse(MCHelper.containsModNameLine(
                Collections.singletonList("§9§oMinecraft§r"), "UIE"));
        assertFalse(MCHelper.containsModNameLine(Collections.<String>emptyList(), "Minecraft"));
    }
}

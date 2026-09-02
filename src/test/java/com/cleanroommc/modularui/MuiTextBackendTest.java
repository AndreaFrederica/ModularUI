package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.text.MuiTextBackends;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MuiTextBackendTest {

    @Test
    void registeredBackendProvidesScaledMeasurementAndFailuresFallBack() {
        MuiTextBackends.register(new com.cleanroommc.modularui.api.text.MuiTextBackend() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public float measure(String text, float scale, int color, boolean shadow) {
                return text.length() * scale;
            }
        });
        try {
            assertTrue(MuiTextBackends.isAvailable());
            assertEquals(3.0f, MuiTextBackends.measure("abc", 1.0f, 0xFFFFFFFF, false));

            MuiTextBackends.register(new com.cleanroommc.modularui.api.text.MuiTextBackend() {
                @Override
                public boolean isAvailable() {
                    throw new LinkageError("optional backend unavailable");
                }

                @Override
                public float measure(String text, float scale, int color, boolean shadow) {
                    return 0;
                }
            });
            assertTrue(Float.isNaN(MuiTextBackends.measure("abc", 1.0f, 0, false)));
        } finally {
            MuiTextBackends.register(null);
        }
    }
}

package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.MuiProtocolPlan;
import com.cleanroommc.modularui.api.sync.MuiValueCodec;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MuiSyncContractTest {

    @Test
    void protocolFingerprintIsCanonicalAndCoversTopology() {
        MuiProtocolPlan first = MuiProtocolPlan.builder("test:screen", 1)
                .handler("state", "value", 1)
                .action("refresh", "command", 1)
                .slot("input", "item", 1, 0)
                .build();
        MuiProtocolPlan same = MuiProtocolPlan.builder("test:screen", 1)
                .handler("state", "value", 1)
                .action("refresh", "command", 1)
                .slot("input", "item", 1, 0)
                .build();
        MuiProtocolPlan changed = MuiProtocolPlan.builder("test:screen", 1)
                .handler("state", "value", 2)
                .action("refresh", "command", 1)
                .slot("input", "item", 1, 0)
                .build();
        assertTrue(first.matches(same));
        assertArrayEquals(first.getCanonicalBytes(), same.getCanonicalBytes());
        assertFalse(first.matches(changed));
        assertEquals(64, first.getFingerprintHex().length());
        assertThrows(IllegalArgumentException.class, () -> MuiProtocolPlan.builder("test:bad", 1)
                .slot("a", "item", 1, 0).slot("b", "item", 1, 0));

        MuiProtocolPlan.Builder atomic = MuiProtocolPlan.builder("test:atomic", 1)
                .slot("a", "item", 1, 0);
        assertThrows(IllegalArgumentException.class, () -> atomic.slot("a", "item", 1, 1));
        atomic.slot("b", "item", 1, 1).build();
    }

    @Test
    void valueCodecRoundTripsStructuredDataAndRejectsLimits() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "storage");
        value.put("revision", 7L);
        value.put("entries", Arrays.asList(1, true, null, 3.5d));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        MuiValueCodec.writeFrame(buffer, value, DocumentSyncLimits.DEFAULT);
        assertEquals(value, MuiValueCodec.readFrame(buffer, DocumentSyncLimits.DEFAULT));

        DocumentSyncLimits tiny = new DocumentSyncLimits(64, 4, 2, 2, 2, 2, 2);
        assertThrows(IllegalArgumentException.class, () -> MuiValueCodec.writeFrame(
                new PacketBuffer(Unpooled.buffer()), "too long", tiny));
        PacketBuffer unknown = new PacketBuffer(Unpooled.buffer());
        unknown.writeVarInt(1).writeByte(127);
        assertThrows(DecoderException.class, () -> MuiValueCodec.readFrame(unknown, tiny));
    }
}

package com.cleanroommc.modularui.value.sync.document;

import io.netty.handler.codec.DecoderException;

public enum DocumentFrameOpcode {
    HELLO(0),
    READY(1),
    SUBSCRIBE(2),
    SUBSCRIBED(3),
    PATCH(4),
    COMMAND(5),
    RESULT(6),
    ERROR(7),
    UNSUBSCRIBE(8),
    RESET(9);

    private final int wireId;

    DocumentFrameOpcode(int wireId) { this.wireId = wireId; }

    public int getWireId() { return this.wireId; }

    public static DocumentFrameOpcode fromWireId(int wireId) {
        for (DocumentFrameOpcode value : values()) if (value.wireId == wireId) return value;
        throw new DecoderException("Unknown document frame opcode: " + wireId);
    }
}

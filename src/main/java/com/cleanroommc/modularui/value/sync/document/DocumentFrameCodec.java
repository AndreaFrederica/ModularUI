package com.cleanroommc.modularui.value.sync.document;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.MuiValueCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;

import java.util.Map;
import java.util.Objects;

/** Strict length-delimited codec for one document protocol frame. */
public final class DocumentFrameCodec {

    private DocumentFrameCodec() {}

    public static void write(PacketBuffer target, DocumentFrame frame, DocumentSyncLimits limits) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(limits, "limits");
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        encoded.writeVarInt(frame.getOpcode().getWireId());
        MuiValueCodec.writeFrame(encoded, frame.getBody(), limits);
        int length = encoded.readableBytes();
        if (length > limits.getMaxFrameBytes()) throw new IllegalArgumentException("Document frame exceeds byte limit: " + length);
        target.writeVarInt(length);
        target.writeBytes(encoded, encoded.readerIndex(), length);
    }

    @SuppressWarnings("unchecked")
    public static DocumentFrame read(PacketBuffer source, DocumentSyncLimits limits) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        try {
            int length = source.readVarInt();
            if (length < 0 || length > limits.getMaxFrameBytes()) throw new DecoderException("Invalid document frame length: " + length);
            if (source.readableBytes() < length) throw new DecoderException("Truncated document frame");
            PacketBuffer frame = new PacketBuffer(Unpooled.copiedBuffer(source.readSlice(length)));
            DocumentFrameOpcode opcode = DocumentFrameOpcode.fromWireId(frame.readVarInt());
            Object body = MuiValueCodec.readFrame(frame, limits);
            if (!(body instanceof Map)) throw new DecoderException("Document frame body must be a map");
            if (frame.isReadable()) throw new DecoderException("Trailing bytes after document frame");
            return DocumentFrame.decoded(opcode, (Map<String, Object>) body);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("Malformed document frame", exception);
        }
    }
}

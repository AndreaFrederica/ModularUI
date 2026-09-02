package com.cleanroommc.modularui.api.sync;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded codec for document sync JSON-like values. */
public final class MuiValueCodec {

    private static final int NULL = 0;
    private static final int FALSE = 1;
    private static final int TRUE = 2;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int DOUBLE = 5;
    private static final int STRING = 6;
    private static final int BYTES = 7;
    private static final int LIST = 8;
    private static final int MAP = 9;

    private MuiValueCodec() {}

    public static void writeFrame(PacketBuffer target, Object value, DocumentSyncLimits limits) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(limits, "limits");
        PacketBuffer frame = new PacketBuffer(Unpooled.buffer());
        writeValue(frame, value, limits, 0, new IdentityHashMap<>());
        int length = frame.readableBytes();
        if (length > limits.getMaxFrameBytes()) throw new IllegalArgumentException("Document frame exceeds byte limit: " + length);
        target.writeVarInt(length);
        target.writeBytes(frame, frame.readerIndex(), length);
    }

    public static Object readFrame(PacketBuffer source, DocumentSyncLimits limits) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        try {
            int length = source.readVarInt();
            if (length < 0 || length > limits.getMaxFrameBytes()) throw new DecoderException("Invalid document frame length: " + length);
            requireReadable(source, length);
            PacketBuffer frame = new PacketBuffer(Unpooled.copiedBuffer(source.readSlice(length)));
            Object value = readValue(frame, limits, 0);
            if (frame.isReadable()) throw new DecoderException("Trailing bytes after document value: " + frame.readableBytes());
            return value;
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("Malformed document value", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(PacketBuffer output, Object value, DocumentSyncLimits limits,
                                   int depth, IdentityHashMap<Object, Boolean> seen) {
        checkDepth(depth, limits);
        if (value == null) {
            output.writeByte(NULL);
        } else if (value instanceof Boolean) {
            output.writeByte((Boolean) value ? TRUE : FALSE);
        } else if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            output.writeByte(INT);
            output.writeInt(((Number) value).intValue());
        } else if (value instanceof Long) {
            output.writeByte(LONG);
            output.writeLong((Long) value);
        } else if (value instanceof Double || value instanceof Float) {
            output.writeByte(DOUBLE);
            output.writeDouble(((Number) value).doubleValue());
        } else if (value instanceof String) {
            output.writeByte(STRING);
            writeString(output, (String) value, limits);
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if (bytes.length > limits.getMaxFrameBytes()) throw new IllegalArgumentException("Byte array exceeds frame limit");
            output.writeByte(BYTES);
            output.writeVarInt(bytes.length);
            output.writeBytes(bytes);
        } else if (value instanceof List) {
            enter(value, seen);
            try {
                List<?> list = (List<?>) value;
                checkCollectionSize(list.size(), limits);
                output.writeByte(LIST);
                output.writeVarInt(list.size());
                for (Object item : list) writeValue(output, item, limits, depth + 1, seen);
            } finally { seen.remove(value); }
        } else if (value instanceof Map) {
            enter(value, seen);
            try {
                Map<?, ?> map = (Map<?, ?>) value;
                checkCollectionSize(map.size(), limits);
                output.writeByte(MAP);
                output.writeVarInt(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("Document map keys must be strings");
                    writeString(output, (String) entry.getKey(), limits);
                    writeValue(output, entry.getValue(), limits, depth + 1, seen);
                }
            } finally { seen.remove(value); }
        } else {
            throw new IllegalArgumentException("Unsupported document value type: " + value.getClass().getName());
        }
        if (output.writerIndex() > limits.getMaxFrameBytes()) {
            throw new IllegalArgumentException("Document frame exceeds byte limit: " + output.writerIndex());
        }
    }

    private static Object readValue(PacketBuffer input, DocumentSyncLimits limits, int depth) {
        checkDecodeDepth(depth, limits);
        if (!input.isReadable()) throw new DecoderException("Truncated document value");
        int type = input.readUnsignedByte();
        switch (type) {
            case NULL: return null;
            case FALSE: return false;
            case TRUE: return true;
            case INT: requireReadable(input, 4); return input.readInt();
            case LONG: requireReadable(input, 8); return input.readLong();
            case DOUBLE: requireReadable(input, 8); return input.readDouble();
            case STRING: return readString(input, limits);
            case BYTES: {
                int length = input.readVarInt();
                if (length < 0 || length > limits.getMaxFrameBytes()) throw new DecoderException("Invalid byte array length: " + length);
                requireReadable(input, length);
                byte[] bytes = new byte[length];
                input.readBytes(bytes);
                return bytes;
            }
            case LIST: {
                int size = readCollectionSize(input, limits);
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(readValue(input, limits, depth + 1));
                return list;
            }
            case MAP: {
                int size = readCollectionSize(input, limits);
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = readString(input, limits);
                    if (map.containsKey(key)) throw new DecoderException("Duplicate document map key: " + key);
                    map.put(key, readValue(input, limits, depth + 1));
                }
                return map;
            }
            default: throw new DecoderException("Unknown document value type: " + type);
        }
    }

    private static void checkDepth(int depth, DocumentSyncLimits limits) {
        if (depth > limits.getMaxNestingDepth()) throw new IllegalArgumentException("Document value nesting exceeds limit");
    }

    private static void checkDecodeDepth(int depth, DocumentSyncLimits limits) {
        if (depth > limits.getMaxNestingDepth()) throw new DecoderException("Document value nesting exceeds limit");
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (seen.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Document values cannot contain cycles");
    }

    private static void checkCollectionSize(int size, DocumentSyncLimits limits) {
        if (size < 0 || size > limits.getMaxCollectionEntries()) {
            throw new IllegalArgumentException("Document collection exceeds entry limit: " + size);
        }
    }

    private static int readCollectionSize(PacketBuffer input, DocumentSyncLimits limits) {
        int size = input.readVarInt();
        if (size < 0 || size > limits.getMaxCollectionEntries()) {
            throw new DecoderException("Invalid document collection size: " + size);
        }
        return size;
    }

    private static void requireReadable(PacketBuffer input, int bytes) {
        if (bytes < 0 || input.readableBytes() < bytes) throw new DecoderException("Truncated document value");
    }

    private static void writeString(PacketBuffer output, String value, DocumentSyncLimits limits) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.getMaxStringBytes()) {
            throw new IllegalArgumentException("Document string exceeds byte limit: " + bytes.length);
        }
        output.writeVarInt(bytes.length);
        output.writeBytes(bytes);
    }

    private static String readString(PacketBuffer input, DocumentSyncLimits limits) {
        int length = input.readVarInt();
        if (length < 0 || length > limits.getMaxStringBytes()) throw new DecoderException("Invalid document string length: " + length);
        requireReadable(input, length);
        String value = input.toString(input.readerIndex(), length, StandardCharsets.UTF_8);
        input.skipBytes(length);
        return value;
    }
}

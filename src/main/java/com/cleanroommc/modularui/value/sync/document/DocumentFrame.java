package com.cleanroommc.modularui.value.sync.document;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import com.cleanroommc.modularui.api.sync.document.DocumentPatch;
import com.cleanroommc.modularui.api.sync.document.DocumentSyncErrorCode;
import io.netty.handler.codec.DecoderException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable logical frame. Dynamic values are encoded only through MuiValueCodec. */
public final class DocumentFrame {

    private final DocumentFrameOpcode opcode;
    private final Map<String, Object> body;

    private DocumentFrame(DocumentFrameOpcode opcode, Map<String, Object> body) {
        this.opcode = Objects.requireNonNull(opcode, "opcode");
        this.body = Collections.unmodifiableMap(new LinkedHashMap<>(body));
        validateShape();
    }

    public DocumentFrameOpcode getOpcode() { return this.opcode; }
    public Map<String, Object> getBody() { return this.body; }

    public int intValue(String key) { return (Integer) required(key, Integer.class); }
    public long longValue(String key) {
        Object value = required(key, Number.class);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new DecoderException("Invalid integer document frame field: " + key);
        }
        return ((Number) value).longValue();
    }
    public String stringValue(String key) { return (String) required(key, String.class); }
    public Object value(String key) { return this.body.get(key); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> mapValue(String key) {
        return (Map<String, Object>) required(key, Map.class);
    }

    public DocumentPatch patch(DocumentSyncLimits limits) {
        return DocumentPatch.fromWire(required("patch", java.util.List.class), limits);
    }

    public DocumentSyncErrorCode errorCode() {
        try { return DocumentSyncErrorCode.valueOf(stringValue("code")); }
        catch (IllegalArgumentException exception) { throw new DecoderException("Unknown document error code", exception); }
    }

    public static DocumentFrame hello(int protocolVersion, long features) {
        return frame(DocumentFrameOpcode.HELLO, "protocolVersion", protocolVersion, "features", features);
    }

    public static DocumentFrame ready(int protocolVersion, DocumentSyncLimits limits) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maxFrameBytes", limits.getMaxFrameBytes());
        values.put("maxStringBytes", limits.getMaxStringBytes());
        values.put("maxCollectionEntries", limits.getMaxCollectionEntries());
        values.put("maxNestingDepth", limits.getMaxNestingDepth());
        values.put("maxActiveChannels", limits.getMaxActiveChannels());
        values.put("maxPendingCommands", limits.getMaxPendingCommands());
        values.put("maxPatchOperations", limits.getMaxPatchOperations());
        return frame(DocumentFrameOpcode.READY, "protocolVersion", protocolVersion, "limits", values);
    }

    public static DocumentFrame subscribe(int requestId, String endpointKey, int schemaVersion, Object params) {
        return frame(DocumentFrameOpcode.SUBSCRIBE, "requestId", requestId, "endpointKey", endpointKey,
                "schemaVersion", schemaVersion, "params", params);
    }

    public static DocumentFrame subscribed(int requestId, int channelId, long revision, Map<String, ?> snapshot) {
        return frame(DocumentFrameOpcode.SUBSCRIBED, "requestId", requestId, "channelId", channelId,
                "revision", revision, "snapshot", snapshot);
    }

    public static DocumentFrame patch(int channelId, long baseRevision, long revision,
                                      DocumentPatch patch, DocumentSyncLimits limits) {
        return frame(DocumentFrameOpcode.PATCH, "channelId", channelId, "baseRevision", baseRevision,
                "revision", revision, "patch", patch.toWire(limits));
    }

    public static DocumentFrame command(int channelId, String commandKey, int requestId,
                                        long expectedRevision, Object payload) {
        return frame(DocumentFrameOpcode.COMMAND, "channelId", channelId, "commandKey", commandKey,
                "requestId", requestId, "expectedRevision", expectedRevision, "payload", payload);
    }

    public static DocumentFrame result(int requestId, long revision, Object payload) {
        return frame(DocumentFrameOpcode.RESULT, "requestId", requestId, "revision", revision, "payload", payload);
    }

    public static DocumentFrame error(int requestId, DocumentSyncErrorCode code, String message) {
        return frame(DocumentFrameOpcode.ERROR, "requestId", requestId, "code", code.name(), "message", message);
    }

    public static DocumentFrame unsubscribe(int channelId, String reason) {
        return frame(DocumentFrameOpcode.UNSUBSCRIBE, "channelId", channelId, "reason", reason);
    }

    public static DocumentFrame reset(int channelId, long revision, Map<String, ?> snapshot) {
        return frame(DocumentFrameOpcode.RESET, "channelId", channelId, "revision", revision, "snapshot", snapshot);
    }

    static DocumentFrame decoded(DocumentFrameOpcode opcode, Map<String, Object> body) {
        try { return new DocumentFrame(opcode, body); }
        catch (IllegalArgumentException exception) { throw new DecoderException(exception.getMessage(), exception); }
    }

    public static DocumentSyncLimits decodeLimits(DocumentFrame frame) {
        Map<String, Object> values = frame.mapValue("limits");
        requireExactKeys(values, "limits", "maxFrameBytes", "maxStringBytes", "maxCollectionEntries",
                "maxNestingDepth", "maxActiveChannels", "maxPendingCommands", "maxPatchOperations");
        try {
            return new DocumentSyncLimits(number(values, "maxFrameBytes"), number(values, "maxStringBytes"),
                    number(values, "maxCollectionEntries"), number(values, "maxNestingDepth"),
                    number(values, "maxActiveChannels"), number(values, "maxPendingCommands"),
                    number(values, "maxPatchOperations"));
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid document limits", exception);
        }
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Integer)) throw new IllegalArgumentException("Document limit must be an integer: " + key);
        return (Integer) value;
    }

    private Object required(String key, Class<?> type) {
        if (!this.body.containsKey(key)) throw new DecoderException("Missing document frame field: " + key);
        Object value = this.body.get(key);
        if (value == null || !type.isInstance(value)) throw new DecoderException("Invalid document frame field: " + key);
        return value;
    }

    private void validateShape() {
        switch (this.opcode) {
            case HELLO:
                exact("protocolVersion", "features"); integers("protocolVersion"); longIntegers("features"); break;
            case READY:
                exact("protocolVersion", "limits"); integers("protocolVersion"); required("limits", Map.class); break;
            case SUBSCRIBE:
                exact("requestId", "endpointKey", "schemaVersion", "params");
                integers("requestId", "schemaVersion"); required("endpointKey", String.class); break;
            case SUBSCRIBED:
                exact("requestId", "channelId", "revision", "snapshot");
                integers("requestId", "channelId"); longIntegers("revision"); required("snapshot", Map.class); break;
            case PATCH:
                exact("channelId", "baseRevision", "revision", "patch");
                integers("channelId"); longIntegers("baseRevision", "revision"); required("patch", java.util.List.class); break;
            case COMMAND:
                exact("channelId", "commandKey", "requestId", "expectedRevision", "payload");
                integers("channelId", "requestId"); longIntegers("expectedRevision"); required("commandKey", String.class); break;
            case RESULT:
                exact("requestId", "revision", "payload"); integers("requestId"); longIntegers("revision"); break;
            case ERROR:
                exact("requestId", "code", "message"); integers("requestId");
                required("code", String.class); required("message", String.class); break;
            case UNSUBSCRIBE:
                exact("channelId", "reason"); integers("channelId"); required("reason", String.class); break;
            case RESET:
                exact("channelId", "revision", "snapshot"); integers("channelId"); longIntegers("revision"); required("snapshot", Map.class); break;
            default: throw new IllegalArgumentException("Unsupported document frame opcode");
        }
    }

    private void exact(String... keys) { requireExactKeys(this.body, this.opcode.name(), keys); }

    private void integers(String... keys) {
        for (String key : keys) required(key, Integer.class);
    }

    private void longIntegers(String... keys) {
        for (String key : keys) {
            Object value = required(key, Number.class);
            if (!(value instanceof Integer) && !(value instanceof Long)) {
                throw new IllegalArgumentException("Document frame field must be an integer: " + key);
            }
        }
    }

    private static void requireExactKeys(Map<?, ?> map, String label, String... keys) {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(keys));
        if (map.size() != expected.size() || !map.keySet().equals(expected)) {
            throw new IllegalArgumentException("Unexpected fields in document " + label);
        }
    }

    private static DocumentFrame frame(DocumentFrameOpcode opcode, Object... pairs) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) body.put((String) pairs[i], pairs[i + 1]);
        return new DocumentFrame(opcode, body);
    }
}

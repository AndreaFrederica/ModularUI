package com.cleanroommc.modularui.api.sync.document;

import com.cleanroommc.modularui.api.sync.DocumentSyncLimits;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, ordered and atomic top-level state patch. */
public final class DocumentPatch {

    private final List<DocumentPatchOperation> operations;

    public DocumentPatch(List<DocumentPatchOperation> operations) {
        Objects.requireNonNull(operations, "operations");
        List<DocumentPatchOperation> copy = new ArrayList<>(operations.size());
        Set<String> keys = new LinkedHashSet<>();
        for (DocumentPatchOperation operation : operations) {
            DocumentPatchOperation checked = Objects.requireNonNull(operation, "operation");
            if (!keys.add(checked.getKey())) {
                throw new IllegalArgumentException("Duplicate patch key: " + checked.getKey());
            }
            copy.add(checked);
        }
        this.operations = Collections.unmodifiableList(copy);
    }

    public static DocumentPatch of(DocumentPatchOperation... operations) {
        List<DocumentPatchOperation> values = new ArrayList<>();
        Collections.addAll(values, operations);
        return new DocumentPatch(values);
    }

    public List<DocumentPatchOperation> getOperations() { return this.operations; }

    public Map<String, Object> apply(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(source);
        for (DocumentPatchOperation operation : this.operations) {
            if (operation.getType() == DocumentPatchOperation.Type.REMOVE) result.remove(operation.getKey());
            else result.put(operation.getKey(), operation.getValue());
        }
        return result;
    }

    public List<Object> toWire(DocumentSyncLimits limits) {
        if (this.operations.size() > limits.getMaxPatchOperations()) {
            throw new IllegalArgumentException("Document patch exceeds operation limit");
        }
        List<Object> result = new ArrayList<>(this.operations.size());
        for (DocumentPatchOperation operation : this.operations) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("op", operation.getType() == DocumentPatchOperation.Type.SET ? "set" : "remove");
            encoded.put("key", operation.getKey());
            if (operation.getType() == DocumentPatchOperation.Type.SET) encoded.put("value", operation.getValue());
            result.add(encoded);
        }
        return result;
    }

    public static DocumentPatch fromWire(Object value, DocumentSyncLimits limits) {
        if (!(value instanceof List)) throw new DecoderException("Document patch must be a list");
        List<?> encoded = (List<?>) value;
        if (encoded.size() > limits.getMaxPatchOperations()) throw new DecoderException("Document patch exceeds operation limit");
        List<DocumentPatchOperation> operations = new ArrayList<>(encoded.size());
        try {
            for (Object item : encoded) {
                if (!(item instanceof Map)) throw new DecoderException("Document patch operation must be a map");
                Map<?, ?> map = (Map<?, ?>) item;
                Object op = map.get("op");
                Object key = map.get("key");
                if (!(op instanceof String) || !(key instanceof String)) throw new DecoderException("Invalid document patch operation");
                if ("set".equals(op)) {
                    if (map.size() != 3 || !map.containsKey("value")) throw new DecoderException("Invalid set patch operation");
                    operations.add(DocumentPatchOperation.set((String) key, map.get("value")));
                } else if ("remove".equals(op)) {
                    if (map.size() != 2) throw new DecoderException("Invalid remove patch operation");
                    operations.add(DocumentPatchOperation.remove((String) key));
                } else {
                    throw new DecoderException("Unknown document patch operation: " + op);
                }
            }
            return new DocumentPatch(operations);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException(exception.getMessage(), exception);
        }
    }
}

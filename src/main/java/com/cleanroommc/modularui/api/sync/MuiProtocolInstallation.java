package com.cleanroommc.modularui.api.sync;

import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.screen.ModularContainer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Runtime objects created from one protocol plan, addressable without reflection. */
public final class MuiProtocolInstallation {

    private final MuiProtocolPlan plan;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean complete;

    MuiProtocolInstallation(MuiProtocolPlan plan) { this.plan = plan; }

    public MuiProtocolPlan getPlan() { return this.plan; }
    public MuiTemplateContract getContract() { return MuiTemplateContract.fromPlan(this.plan); }

    public @Nullable Object find(MuiProtocolEntry.Kind kind, String key, int numericId) {
        return this.values.get(identity(kind, key, numericId));
    }

    public Object get(MuiProtocolEntry.Kind kind, String key, int numericId) {
        Object value = find(kind, key, numericId);
        if (value == null) throw new NoSuchElementException("Protocol entry is not installed: " + key + ':' + numericId);
        return value;
    }

    public SyncHandler getHandler(String key, int numericId) {
        return (SyncHandler) get(MuiProtocolEntry.Kind.HANDLER, key, numericId);
    }

    public ItemSlotSH getSlot(String key, int numericId) {
        return (ItemSlotSH) get(MuiProtocolEntry.Kind.SLOT, key, numericId);
    }

    public MuiProtocolAction getAction(String key) {
        return (MuiProtocolAction) get(MuiProtocolEntry.Kind.ACTION, key, -1);
    }

    public boolean isComplete() { return this.complete; }

    /** Verifies that real protocol slots occupy their declared leading container ordinals. */
    public void verifyContainerSlots(ModularContainer container) {
        if (!this.complete) throw new IllegalStateException("Protocol installation is incomplete");
        int expectedOrdinal = 0;
        for (MuiProtocolEntry entry : this.plan.getEntries()) {
            if (entry.getKind() != MuiProtocolEntry.Kind.SLOT) continue;
            if (entry.getOrder() != expectedOrdinal) {
                throw new IllegalStateException("Protocol slot ordinals must be contiguous from zero");
            }
            ItemSlotSH handler = getSlot(entry.getKey(), entry.getNumericId());
            if (container.inventorySlots.size() <= expectedOrdinal
                    || container.inventorySlots.get(expectedOrdinal) != handler.getSlot()) {
                throw new IllegalStateException("Protocol slot was not registered at container ordinal "
                        + expectedOrdinal + ": " + entry.getKey() + ':' + entry.getNumericId());
            }
            expectedOrdinal++;
        }
    }

    public @UnmodifiableView Map<String, Object> entries() {
        return Collections.unmodifiableMap(this.values);
    }

    void put(MuiProtocolEntry entry, Object value) {
        if (this.complete) throw new IllegalStateException("Protocol installation is complete");
        String identity = identity(entry.getKind(), entry.getKey(), entry.getNumericId());
        if (this.values.putIfAbsent(identity, value) != null) {
            throw new IllegalStateException("Protocol entry was installed twice: " + entry.getKey());
        }
    }

    void complete() { this.complete = true; }

    private static String identity(MuiProtocolEntry.Kind kind, String key, int numericId) {
        return kind.name() + '\0' + key + '\0' + numericId;
    }
}

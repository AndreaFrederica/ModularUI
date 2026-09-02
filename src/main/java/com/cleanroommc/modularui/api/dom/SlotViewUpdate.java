package com.cleanroommc.modularui.api.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of a worker-side item-grid rebuild. The client thread moves fixed, pre-registered slot views
 * between a visible parent and a disabled parking parent; it never creates or destroys a real container slot.
 */
public final class SlotViewUpdate {

    private final NodeHandle visibleParent;
    private final NodeHandle parkingParent;
    private final List<NodeHandle> slotTable;
    private final List<NodeHandle> visibleSlots;

    public SlotViewUpdate(NodeHandle visibleParent, NodeHandle parkingParent,
                          List<NodeHandle> slotTable, List<NodeHandle> visibleSlots) {
        this.visibleParent = requireHandle(visibleParent, "visibleParent");
        this.parkingParent = requireHandle(parkingParent, "parkingParent");
        if (this.visibleParent.equals(this.parkingParent)) {
            throw new IllegalArgumentException("Visible and parking parents must be different");
        }
        this.slotTable = immutableUnique(slotTable, "slotTable");
        this.visibleSlots = immutableUnique(visibleSlots, "visibleSlots");
        if (!new HashSet<>(this.slotTable).containsAll(this.visibleSlots)) {
            throw new IllegalArgumentException("Every visible slot must be present in slotTable");
        }
    }

    public NodeHandle getVisibleParent() {
        return this.visibleParent;
    }

    public NodeHandle getParkingParent() {
        return this.parkingParent;
    }

    public List<NodeHandle> getSlotTable() {
        return this.slotTable;
    }

    public List<NodeHandle> getVisibleSlots() {
        return this.visibleSlots;
    }

    private static NodeHandle requireHandle(NodeHandle handle, String name) {
        Objects.requireNonNull(handle, name);
        if (!handle.isPresent()) throw new IllegalArgumentException(name + " must be present");
        return handle;
    }

    private static List<NodeHandle> immutableUnique(List<NodeHandle> handles, String name) {
        Objects.requireNonNull(handles, name);
        List<NodeHandle> copy = new ArrayList<>(handles.size());
        Set<NodeHandle> unique = new HashSet<>();
        for (NodeHandle handle : handles) {
            requireHandle(handle, name + " entry");
            if (!unique.add(handle)) throw new IllegalArgumentException(name + " contains a duplicate handle");
            copy.add(handle);
        }
        return Collections.unmodifiableList(copy);
    }
}

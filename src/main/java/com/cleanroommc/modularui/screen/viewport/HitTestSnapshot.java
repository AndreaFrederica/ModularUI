package com.cleanroommc.modularui.screen.viewport;

import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable per-frame view of the existing ModularUI hit-test result. */
public final class HitTestSnapshot {

    static final HitTestSnapshot EMPTY = new HitTestSnapshot(0, -1, -1,
            Collections.emptyList(), Collections.emptyList(), null);

    private final long sequence;
    private final long structureRevision;
    private final long geometryRevision;
    private final List<IWidget> belowMouse;
    private final List<IWidget> hovered;
    private final IWidget target;

    private HitTestSnapshot(long sequence, long structureRevision, long geometryRevision,
                            List<IWidget> belowMouse, List<IWidget> hovered, @Nullable IWidget target) {
        this.sequence = sequence;
        this.structureRevision = structureRevision;
        this.geometryRevision = geometryRevision;
        this.belowMouse = belowMouse;
        this.hovered = hovered;
        this.target = target;
    }

    static HitTestSnapshot capture(long sequence, long structureRevision, long geometryRevision,
                                   List<LocatedWidget> belowMouse, List<LocatedWidget> hovered,
                                   @Nullable IWidget target) {
        return new HitTestSnapshot(sequence, structureRevision, geometryRevision,
                copyWidgets(belowMouse), copyWidgets(hovered), target);
    }

    private static List<IWidget> copyWidgets(List<LocatedWidget> locatedWidgets) {
        if (locatedWidgets.isEmpty()) return Collections.emptyList();
        List<IWidget> widgets = new ArrayList<>(locatedWidgets.size());
        for (LocatedWidget locatedWidget : locatedWidgets) widgets.add(locatedWidget.getElement());
        return Collections.unmodifiableList(widgets);
    }

    public long getSequence() {
        return this.sequence;
    }

    public boolean matchesRevisions(long structureRevision, long geometryRevision) {
        return this.structureRevision == structureRevision && this.geometryRevision == geometryRevision;
    }

    public @UnmodifiableView List<IWidget> getBelowMouse() {
        return this.belowMouse;
    }

    public @UnmodifiableView List<IWidget> getHovered() {
        return this.hovered;
    }

    public @Nullable IWidget getTarget() {
        return this.target;
    }
}

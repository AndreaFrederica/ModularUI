package com.cleanroommc.modularui.api.dom;

import com.cleanroommc.modularui.api.widget.IWidget;

import org.jetbrains.annotations.ApiStatus;

public final class LegacyWidgetElement extends MuiElement {

    private final IWidget widget;
    private final boolean opaque;

    LegacyWidgetElement(MuiDocument document, NodeHandle handle, String tagName, IWidget widget,
                        boolean opaque) {
        super(document, handle, tagName);
        this.widget = widget;
        this.opaque = opaque;
    }

    public boolean isOpaque() {
        return this.opaque;
    }

    public String getWidgetClassName() {
        return this.widget.getClass().getName();
    }

    @ApiStatus.Internal
    public IWidget getWidgetInternal() {
        return this.widget;
    }
}

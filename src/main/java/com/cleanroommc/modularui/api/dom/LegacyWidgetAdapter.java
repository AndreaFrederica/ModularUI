package com.cleanroommc.modularui.api.dom;

import com.cleanroommc.modularui.api.widget.IWidget;

import java.util.Map;

public interface LegacyWidgetAdapter<T extends IWidget> {

    String getTagName();

    default void collectAttributes(T widget, Map<String, String> attributes) {}
}

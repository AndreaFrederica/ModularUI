package com.cleanroommc.modularui.api.component;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.widget.IWidget;

@FunctionalInterface
public interface MuiElementFactory<W extends IWidget> {

    W create(MuiElement element);
}

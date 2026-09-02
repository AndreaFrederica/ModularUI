package com.cleanroommc.modularui.api.dom;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface MuiDocumentHost {

    default void checkAccess() {}

    default void validateMutations(List<DomMutation> mutations) {}

    default void applyMutations(List<DomMutation> mutations) {}

    /** Called after the logical DOM has committed the same mutation batch to the host. */
    default void onMutationsApplied(List<DomMutation> mutations) {}

    default void onAttributeChanged(MuiElement element, String name, String oldValue, String newValue) {}

    default void onTextChanged(MuiText text, String oldValue, String newValue) {}

    default void onNodesDestroyed(List<MuiNode> nodes) {}

    /** Client-local viewport property bridge. Non-viewport hosts return zero/no-op. */
    default int getScrollLeft(MuiElement element) { return 0; }

    default int getScrollTop(MuiElement element) { return 0; }

    default int getScrollWidth(MuiElement element) { return 0; }

    default int getScrollHeight(MuiElement element) { return 0; }

    default void setScrollPosition(MuiElement element, int left, int top) {}

    default void onDocumentClosed() {}
}

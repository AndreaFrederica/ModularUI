package com.cleanroommc.modularui.api.event;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.event.EventListenerRegistry;
import com.cleanroommc.modularui.screen.event.MuiEventDispatcher;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface IEventTarget {

    default @Nullable IEventTarget getEventParent() {
        return null;
    }

    default <E extends MuiEvent> EventSubscription addEventListener(
            MuiEventType<E> type, MuiEventListener<? super E> listener) {
        return addEventListener(type, listener, EventListenerOptions.DEFAULT);
    }

    default <E extends MuiEvent> EventSubscription addEventListener(
            MuiEventType<E> type, MuiEventListener<? super E> listener, EventListenerOptions options) {
        return EventListenerRegistry.add(this, type, listener, options);
    }

    default <E extends MuiEvent> void removeEventListener(
            MuiEventType<E> type, MuiEventListener<? super E> listener) {
        removeEventListener(type, listener, false);
    }

    default <E extends MuiEvent> void removeEventListener(
            MuiEventType<E> type, MuiEventListener<? super E> listener, boolean capture) {
        EventListenerRegistry.remove(this, type, listener, capture);
    }

    default boolean dispatchEvent(MuiEvent event) {
        Objects.requireNonNull(event, "event");
        if (this instanceof IWidget widget && widget.isValid()) {
            return widget.getScreen().getEventDispatcher().dispatch(this, event);
        }
        return MuiEventDispatcher.dispatchDetached(this, event);
    }

    default void setPointerCapture(int pointerId) {
        MuiEventDispatcher.forTarget(this).setPointerCapture(pointerId, this);
    }

    default void releasePointerCapture(int pointerId) {
        MuiEventDispatcher.forTarget(this).releasePointerCapture(pointerId, this);
    }

    default boolean hasPointerCapture(int pointerId) {
        return MuiEventDispatcher.forTarget(this).hasPointerCapture(pointerId, this);
    }
}

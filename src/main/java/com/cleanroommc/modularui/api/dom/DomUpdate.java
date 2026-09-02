package com.cleanroommc.modularui.api.dom;

/**
 * A client-thread document update. Implementations queued from a worker thread must only capture immutable data;
 * they are executed later at a screen safe point inside one mutation transaction.
 */
@FunctionalInterface
public interface DomUpdate {

    void apply(MuiDocument document);
}

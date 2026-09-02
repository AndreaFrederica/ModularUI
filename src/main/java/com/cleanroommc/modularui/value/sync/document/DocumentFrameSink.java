package com.cleanroommc.modularui.value.sync.document;

@FunctionalInterface
public interface DocumentFrameSink {

    void send(DocumentFrame frame);
}

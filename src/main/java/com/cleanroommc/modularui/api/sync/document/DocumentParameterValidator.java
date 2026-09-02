package com.cleanroommc.modularui.api.sync.document;

/** Validates decoded endpoint parameters before endpoint code is invoked. */
@FunctionalInterface
public interface DocumentParameterValidator {

    void validate(Object params) throws DocumentSyncException;
}

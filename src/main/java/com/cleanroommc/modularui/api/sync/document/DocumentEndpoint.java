package com.cleanroommc.modularui.api.sync.document;

/** Opens one server-owned endpoint subscription. */
@FunctionalInterface
public interface DocumentEndpoint {

    DocumentEndpointSubscription open(DocumentEndpointContext context, Object params,
                                      DocumentEndpointObserver observer) throws DocumentSyncException;
}

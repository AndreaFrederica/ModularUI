package com.cleanroommc.modularui.api.navigation;

/** Handles semantic actions which cannot be represented as a normal pointer click. */
public interface INavigationActionHandler {

    NavigationActionResult onNavigationAction(NavigationAction action);
}

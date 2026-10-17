package com.example.read.navigation

import dev.folio.model.workspace.AppDestination
import dev.folio.ui.icon.IconId

internal data class NavigationItem(val destination: AppDestination, val label: String, val icon: IconId)
internal val primaryNavigation = listOf(
    NavigationItem(AppDestination.HOME, "Home", IconId.HOME),
    NavigationItem(AppDestination.WORKSPACE, "Workspace", IconId.WORKSPACE),
    NavigationItem(AppDestination.LEARNING, "Learning", IconId.LEARNING),
    NavigationItem(AppDestination.ASSISTANT, "Assistant", IconId.ASSISTANT))
internal val secondaryNavigation = listOf(
    NavigationItem(AppDestination.REVISION, "Revision", IconId.REVISION),
    NavigationItem(AppDestination.NOTES, "Saved notes", IconId.BOOKMARK),
    NavigationItem(AppDestination.SETTINGS, "Appearance", IconId.SETTINGS),
    NavigationItem(AppDestination.EXPLORER, "UI explorer", IconId.EXPLORER))
internal fun AppDestination.navigationGroup() = when (this) {
    AppDestination.SUBJECT, AppDestination.CHAPTER, AppDestination.SEARCH -> AppDestination.HOME
    AppDestination.PDF, AppDestination.STUDY -> AppDestination.WORKSPACE
    AppDestination.NOTES, AppDestination.REVISION -> AppDestination.LEARNING
    else -> this
}

package dev.folio.ui.media

import androidx.compose.runtime.staticCompositionLocalOf

/** Cache publication invalidates source renderers without replacing their remembered interaction state. */
val LocalSourceRevision = staticCompositionLocalOf { 0L }

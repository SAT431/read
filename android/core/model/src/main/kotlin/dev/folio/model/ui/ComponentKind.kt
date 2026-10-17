package dev.folio.model.ui

import kotlinx.serialization.Serializable

@Serializable enum class ComponentKind { READER, QUIZ, EXPLANATION, COMPARISON, DIAGRAM, RECALL }
@Serializable enum class Presentation { SURFACE, DOMINANT, INLINE, OVERLAY, CAPSULE }
@Serializable enum class ComponentAction { FOCUS, HIDE, PIN, SUSPEND, RESUME, SELECT, SUBMIT, ZOOM, ANNOTATE, NAVIGATE }
@Serializable enum class MotionId { FADE, EXPAND, SLIDE, FOCUS }
@Serializable enum class GestureId { TAP, LONG_PRESS, NATIVE_SELECTION, SCROLL, TRANSFORM }

package dev.folio.model.workspace

import kotlinx.serialization.Serializable

@Serializable enum class LearningFilter { ALL, MISTAKES, CONFIDENT_MISTAKES, UNCERTAIN_CORRECT, RECALL_REPORTS }

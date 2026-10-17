package dev.folio.model.workspace

import dev.folio.model.activity.Activity
import dev.folio.model.ui.ComponentInstance
import kotlinx.serialization.Serializable

@Serializable
data class Workspace(val schemaVersion: Int = 1, val revision: Long = 0,
    val activities: Map<String, Activity> = emptyMap(),
    val components: Map<String, ComponentInstance> = emptyMap(),
    val focusedComponentId: String? = null, val context: StudyContext = StudyContext(),
    val preferences: UserPreferences = UserPreferences(), val navigation: AppNavigation = AppNavigation(),
    val sourceCorrections: Map<String, dev.folio.model.source.SourceCorrection> = emptyMap(),
    val correctionDrafts: Map<String, dev.folio.model.source.SourceCorrectionDraft> = emptyMap(),
    val revisionItems: Map<String, dev.folio.model.activity.RevisionItem> = emptyMap(),
    val studyPlans: Map<String, dev.folio.model.study.DocumentStudyPlan> = emptyMap())

@Serializable
data class UserPreferences(val darkTheme: Boolean = false, val readingSizeSp: Int = 20,
    val reducedMotion: Boolean = false, val voiceCaptions: Boolean = true,
    val voiceAutomaticSend: Boolean = true)

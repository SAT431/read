package dev.folio.model.command

import dev.folio.model.activity.ActivityData
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.ui.MotionId
import dev.folio.model.ui.Presentation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WorkspaceCommand {
    @Serializable @SerialName("CREATE_ACTIVITY")
    data class CreateActivity(val activityId: String, val componentId: String, val kind: ComponentKind,
        val source: SourceAnchor, val data: ActivityData) : WorkspaceCommand
    @Serializable @SerialName("SHOW_COMPONENT")
    data class ShowComponent(val componentId: String, val activityId: String, val kind: ComponentKind) : WorkspaceCommand
    @Serializable @SerialName("FOCUS_ACTIVITY")
    data class FocusActivity(val activityId: String) : WorkspaceCommand
    @Serializable @SerialName("SUSPEND_ACTIVITY")
    data class SuspendActivity(val activityId: String) : WorkspaceCommand
    @Serializable @SerialName("RESUME_ACTIVITY")
    data class ResumeActivity(val activityId: String) : WorkspaceCommand
    @Serializable @SerialName("HIDE_COMPONENT")
    data class HideComponent(val componentId: String) : WorkspaceCommand
    @Serializable @SerialName("SET_PRESENTATION")
    data class SetPresentation(val componentId: String, val presentation: Presentation) : WorkspaceCommand
    @Serializable @SerialName("SET_PRIORITY")
    data class SetPriority(val componentId: String, val priority: Int) : WorkspaceCommand
    @Serializable @SerialName("PLAY_TRANSITION")
    data class PlayTransition(val componentId: String, val motion: MotionId) : WorkspaceCommand
    @Serializable @SerialName("FOCUS_SOURCE")
    data class FocusSource(val source: SourceAnchor) : WorkspaceCommand
    @Serializable @SerialName("OPEN_CHAPTER")
    data class OpenChapter(val chapterId: String) : WorkspaceCommand
    @Serializable @SerialName("OPEN_PAGE")
    data class OpenPage(val documentId: String, val pageIndex: Int) : WorkspaceCommand
    @Serializable @SerialName("NEXT_PAGE") data object NextPage : WorkspaceCommand
    @Serializable @SerialName("PREVIOUS_PAGE") data object PreviousPage : WorkspaceCommand
    @Serializable @SerialName("SHOW_ORIGINAL") data class ShowOriginal(val visible: Boolean = true) : WorkspaceCommand
}

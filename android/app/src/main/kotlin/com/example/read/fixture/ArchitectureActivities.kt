package com.example.read.fixture

import dev.folio.model.activity.*
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import java.util.UUID

object ArchitectureActivities {
    private val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
    fun initial(): Workspace {
        val reader = Activity("reader", SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0), ActivityData.Reader())
        val component = ComponentInstance("reader-view", reader.id, ComponentKind.READER)
        return Workspace(activities = mapOf(reader.id to reader), components = mapOf(component.id to component),
            focusedComponentId = component.id, context = StudyContext(ArchitectureSources.CHAPTER_ID, reader.source, reader.id))
    }
    fun create(kind: ComponentKind): WorkspaceCommand.CreateActivity {
        val data = when (kind) {
            ComponentKind.READER -> ActivityData.Reader()
            ComponentKind.RECALL -> ActivityData.Recall("Explorer fixture: how does rough ER differ from smooth ER?",
                "Fixture reference: rough ER has ribosomes on its surface; smooth ER lacks them. Compare the supporting source.", listOf(source))
            ComponentKind.QUIZ -> ActivityData.Quiz(listOf(Question("q1", "Which feature distinguishes rough ER?",
                listOf("Surface ribosomes", "A separate nucleus", "No membrane", "Photosynthetic pigments"), 0,
                "The fixture passage identifies ribosomes on the rough ER surface.", listOf(source))))
            ComponentKind.EXPLANATION -> ActivityData.Explanation("Deterministic explorer fixture: compare organelles by their structure and the processes they support. This is not a live AI response.")
            ComponentKind.COMPARISON -> ActivityData.Comparison(listOf("Feature", "Rough ER", "Smooth ER"),
                listOf(listOf("Surface", "Ribosomes", "No ribosomes"), listOf("Role", "Protein synthesis", "Lipid synthesis")))
            ComponentKind.DIAGRAM -> ActivityData.Diagram()
        }
        val id = UUID.randomUUID().toString()
        val anchor = if (kind == ComponentKind.DIAGRAM) source.copy(elementId = "cell-figure") else source
        return WorkspaceCommand.CreateActivity("activity-$id", "component-$id", kind, anchor, data)
    }
}

package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents

internal object TestFixture {
    val source = SourceAnchor("doc", 0, "p1")
    val catalog = object : SourceCatalog {
        override fun documents() = listOf(SourceDocument("doc", "Chapter", 4, "hash", null))
        override fun chapters() = listOf(Chapter("chapter", "Chapter", "doc", 1, 2))
        override fun page(documentId: String, pageIndex: Int) = SourcePage("doc", pageIndex, "Page", listOf(
            SourceElement("p1", SourceElementKind.PARAGRAPH, "Source.", 0),
            SourceElement("figure", SourceElementKind.FIGURE, "Figure", 1),
        ), isFixture = true)
        override fun contains(anchor: SourceAnchor) = anchor.documentId == "doc" && anchor.pageIndex in 0..3 &&
            (anchor.elementId == null || anchor.elementId in setOf("p1", "figure"))
    }
    val registry = BuiltInComponents.registry()
    val executor = CommandExecutor(registry, catalog)
    val user = ExecutionPolicy(CommandActor.USER)
    val ai = ExecutionPolicy(CommandActor.AI)
    fun reader(): Workspace {
        val activity = Activity("read", source, ActivityData.Reader())
        val component = ComponentInstance("read-view", activity.id, ComponentKind.READER)
        return Workspace(activities = mapOf(activity.id to activity), components = mapOf(component.id to component),
            focusedComponentId = component.id, context = StudyContext(source = source))
    }
    fun quiz(id: String = "quiz") = WorkspaceCommand.CreateActivity(id, "$id-view", ComponentKind.QUIZ, source,
        ActivityData.Quiz(listOf(Question("q1", "Question", listOf("A", "B", "C", "D"), 0, "Source explanation", listOf(source)))))
    fun execute(state: Workspace, vararg commands: WorkspaceCommand, policy: ExecutionPolicy = user) =
        executor.execute(state, CommandBatch(requestId = "test", commands = commands.toList()), policy)
}

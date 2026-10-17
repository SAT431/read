package dev.folio.runtime.action

import dev.folio.model.library.*
import dev.folio.model.port.CurriculumCatalog
import dev.folio.model.workspace.*
import dev.folio.model.activity.Activity
import dev.folio.model.ui.ComponentInstance
import dev.folio.model.ui.ComponentContract
import dev.folio.runtime.registry.ComponentRegistry
import dev.folio.runtime.registry.BuiltInComponents
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class SubjectReference(val id: String, val title: String)
@Serializable private data class ChapterReference(val id: String, val subject: String, val title: String)
@Serializable private data class PdfReference(val id: String, val chapterId: String, val documentId: String, val title: String, val pages: Int)
@Serializable private data class CapabilityContext(val schemaVersion: Int = 1, val revision: Long,
    val baseline: String, val navigation: AppNavigation, val study: StudyContext,
    val preferences: UserPreferences, val activities: Map<String, Activity>,
    val components: Map<String, ComponentInstance>, val focusedComponentId: String?,
    val subjects: List<SubjectReference>, val chapters: List<ChapterReference>, val pdfs: List<PdfReference>,
    val actions: List<AppActionContract>, val componentContracts: List<ComponentContract>,
    val sourceCorrections: Map<String, dev.folio.model.source.SourceCorrection>,
    val revisionItems: Map<String, dev.folio.model.activity.RevisionItem>, val nowEpochMillis: Long,
    val revisionSuggestions: List<dev.folio.model.activity.RevisionSuggestion>,
    val learningObservations: List<dev.folio.model.activity.LearningObservation>,
    val learningSummary: dev.folio.model.activity.LearningSummary, val learningObservationsTruncated: Boolean,
    val quizHistoryOmitted: Map<String, Int>, val readingProgress: Map<String, dev.folio.model.source.ReadingProgress>,
    val unavailableSources: List<dev.folio.model.source.SourceAnchor>, val unavailableSourceCount: Int, val sourceAvailabilityKnown: Boolean,
    val savedAnnotations: List<dev.folio.model.source.SavedAnnotationReference>, val savedAnnotationCount: Int, val privateDraftCount: Int, val search: dev.folio.model.search.StudySearchSnapshot?, val searchPending: Boolean)

/** Actual state, native time and registered actions for the direct Gemini adapter. */
class AppCapabilityContext(private val catalog: CurriculumCatalog,
    private val registry: ComponentRegistry = BuiltInComponents.registry(),
    private val sourceCatalog: dev.folio.model.port.SourceCatalog? = null, private val cacheRepairAvailable: Boolean = false,
    private val searchSnapshot: (Workspace) -> dev.folio.model.search.StudySearchSnapshot? = { null },
    private val documentStudyAvailable: Boolean = false) {
    private val json = Json { encodeDefaults = true; classDiscriminator = "type" }
    fun encode(state: Workspace, pdfs: List<LibraryPdf>): String {
        val observations = dev.folio.runtime.practice.LearningMemory.observations(state)
        val unavailable = sourceCatalog?.let { dev.folio.runtime.source.SavedSourceReferences.unavailable(state, it) }.orEmpty()
        val savedAnnotations = state.activities.flatMap { (activityId, activity) ->
            (activity.data as? dev.folio.model.activity.ActivityData.Reader)?.annotations?.values.orEmpty().map { note ->
                dev.folio.model.source.SavedAnnotationReference(activityId, note.id, dev.folio.runtime.reading.SavedNotes.fingerprint(activityId, note), note.source, note.kind,
                    sourceCatalog?.let { runCatching { dev.folio.runtime.reading.SavedNotes.resolve(note.source, note.sentenceId, it) }.isSuccess } == true)
            }
        }
        val search = searchSnapshot(state)
        return json.encodeToString(CapabilityContext(
        search = search?.copy(query = ""), searchPending = dev.folio.runtime.search.StudySearchSession.active(state) && state.navigation.globalQuery.isNotBlank() && search == null,
        savedAnnotations = savedAnnotations.take(200), savedAnnotationCount = savedAnnotations.size,
        privateDraftCount = state.activities.values.sumOf { (it.data as? dev.folio.model.activity.ActivityData.Reader)?.annotationDrafts?.size ?: 0 },
        unavailableSources = unavailable.take(200), unavailableSourceCount = unavailable.size, sourceAvailabilityKnown = sourceCatalog != null,
        readingProgress = state.activities.mapNotNull { (id, activity) ->
            val reader = activity.data as? dev.folio.model.activity.ActivityData.Reader
            val page = sourceCatalog?.page(activity.source.documentId, activity.source.pageIndex)
            if (reader == null || page == null) null else id to dev.folio.runtime.reading.ReadingConfirmations.progress(reader, page, sourceCatalog)
        }.toMap(),
        learningObservations = observations.sortedBy { if (it.activityId == state.context.focusedActivityId) 0 else 1 }.take(200),
        quizHistoryOmitted = state.activities.mapNotNull { (id, activity) ->
            (activity.data as? dev.folio.model.activity.ActivityData.Quiz)?.let { quiz ->
                val kept = quiz.pastAttempts.takeLast(3).map { it.number }.toSet() + listOfNotNull(quiz.review?.attemptNumber)
                (quiz.pastAttempts.size - kept.size).takeIf { it > 0 }?.let { id to it }
            }
        }.toMap(),
        learningSummary = dev.folio.runtime.practice.LearningMemory.summary(observations), learningObservationsTruncated = observations.size > 200,
        revisionSuggestions = dev.folio.runtime.practice.RevisionSuggestions.forWorkspace(state, sourceCatalog),
        revisionItems = state.revisionItems, nowEpochMillis = System.currentTimeMillis(),
        revision = state.revision, baseline = catalog.baseline, navigation = state.navigation.copy(notesQuery = "", globalQuery = ""), study = state.context,
        preferences = state.preferences,
        activities = state.activities.mapValues { (_, activity) ->
            when (val data = activity.data) {
                is dev.folio.model.activity.ActivityData.Reader -> activity.copy(data = data.copy(annotationDrafts = emptyMap()))
                is dev.folio.model.activity.ActivityData.Quiz -> {
                    val kept = data.pastAttempts.takeLast(3).map { it.number }.toSet() + listOfNotNull(data.review?.attemptNumber)
                    activity.copy(data = data.copy(pastAttempts = data.pastAttempts.filter { it.number in kept }))
                }
                is dev.folio.model.activity.ActivityData.Recall -> if (data.revealed) activity else activity.copy(data = data.copy(response = ""))
                else -> activity
            }
        }, components = state.components, focusedComponentId = state.focusedComponentId,
        subjects = catalog.subjects().map { SubjectReference(it.id.name, it.title) },
        chapters = catalog.chapters().map { ChapterReference(it.id, it.subject.name, it.title) },
        pdfs = pdfs.map { PdfReference(it.id, it.chapterId, it.document.id, it.document.title, it.document.pageCount) },
        actions = AppActionRegistry.all.map { action ->
            if (action.id in setOf("annotation.draft", "source.correction.draft", "source.correction.approve", "source.correction.remove", "source.cache.confirm_repair", "notes.edit_note", "notes.edit_draft", "notes.commit_draft", "notes.discard_draft")) action.copy(enabled = false)
            else if (action.id.startsWith("harness.")) action.copy(enabled = documentStudyAvailable)
            else if (action.id.startsWith("study.")) action.copy(enabled = documentStudyAvailable && state.navigation.destination == AppDestination.STUDY)
            else if (action.id.startsWith("source.cache.")) action.copy(enabled = cacheRepairAvailable)
            else if (action.id != "reader.focus_sentence") action else action.copy(enabled = state.activities.values.any { activity ->
                activity.data is dev.folio.model.activity.ActivityData.Reader && sourceCatalog
                    ?.page(activity.source.documentId, activity.source.pageIndex)?.let {
                        dev.folio.runtime.reading.SentenceIndex().compile(it).isNotEmpty()
                    } == true
            })
        },
        componentContracts = registry.all(),
        sourceCorrections = state.sourceCorrections.filterValues { it.status in setOf(
            dev.folio.model.source.CorrectionStatus.PROPOSED, dev.folio.model.source.CorrectionStatus.APPROVED) },
    ))
    }
}

package dev.folio.runtime.reading

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.ReadingConfirmation
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class ReadingConfirmationReducer(private val catalog: SourceCatalog, private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val activityId = when (event) {
            is LearningInteraction.ShowReadingCheckpoints -> event.activityId
            is LearningInteraction.OpenReadingConfirmation -> event.activityId
            is LearningInteraction.ConfirmReading -> event.activityId
            is LearningInteraction.ClearReadingConfirmation -> event.activityId
            else -> error("Not a reading confirmation")
        }
        val activity = state.activities[activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Reading confirmation requires a reader")
        if (event is LearningInteraction.OpenReadingConfirmation) {
            val record = reader.readingConfirmations[event.id] ?: error("Unknown reading confirmation")
            require(record.version == event.expectedVersion) { "Reading confirmation changed; refresh before opening" }
            require(ReadingConfirmations.isCurrent(record, catalog)) { "Saved reading target changed or is unavailable; the report is retained" }
            val index = ReadingTargetResolver.resolve(record.source, record.sentenceId, record.documentChecksum, record.textFingerprint, catalog)
            return SavedReadingNavigation(catalog).open(state, activityId, record.source, record.sentenceId, index, 0, actor)
        }
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any {
            it.activityId == activityId && it.visible && it.presentation != Presentation.CAPSULE
        }) { "Show and resume the reader first" }
        if (event is LearningInteraction.ShowReadingCheckpoints) return state.copy(revision = state.revision + 1,
            activities = state.activities + (activityId to activity.copy(data = reader.copy(readingCheckpointsVisible = event.visible))))
        require(reader.readingRevision < 1_000_000) { "Reading report limit reached" }
        val revision = reader.readingRevision + 1
        val records = when (event) {
            is LearningInteraction.ConfirmReading -> {
                require(reader.readingRevision == event.expectedReadingRevision) { "Reading reports changed; refresh before confirming" }
                require(event.source.documentId == activity.source.documentId && event.source.pageIndex == activity.source.pageIndex) { "Confirm only the reader's current page" }
                val page = catalog.page(event.source.documentId, event.source.pageIndex) ?: error("Page unavailable")
                val element = page.elements.firstOrNull { it.id == event.source.elementId && it.kind in ReadingConfirmations.kinds }
                    ?: error("Choose a readable passage or sentence")
                val sentence = event.sentenceId?.let { target -> SentenceIndex().compile(page).firstOrNull { it.id == target && it.source == event.source }
                    ?: error("Reading sentence unavailable") }
                val text = page.corrections[element.id] ?: element.originalText
                require(text.isNotBlank()) { "No readable wording to confirm" }
                val checksum = catalog.documents().firstOrNull { it.id == event.source.documentId }?.checksum ?: error("Document unavailable")
                val id = ReadingConfirmations.id(event.source, event.sentenceId)
                require(id in reader.readingConfirmations || reader.readingConfirmations.size < 1000) { "Reading checkpoint limit reached" }
                reader.readingConfirmations + (id to ReadingConfirmation(id, event.source, sentence?.id, checksum,
                    ReadingTargetResolver.fingerprint(text), (sentence?.text ?: text).take(160), now(), revision, actor == CommandActor.AI))
            }
            is LearningInteraction.ClearReadingConfirmation -> {
                val record = reader.readingConfirmations[event.id] ?: error("Unknown reading confirmation")
                require(record.version == event.expectedVersion) { "Reading confirmation changed; refresh before clearing" }
                reader.readingConfirmations - record.id
            }
            else -> error("Not a reading confirmation")
        }
        val next = reader.copy(readingConfirmations = records, readingRevision = revision)
        ReadingConfirmations.validate(next)
        return state.copy(revision = state.revision + 1, activities = state.activities + (activityId to activity.copy(data = next)))
    }
}

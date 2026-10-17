package dev.folio.ui.icon

enum class IconId { PHYSICS, CHEMISTRY, BOTANY, ZOOLOGY, HOME, LIBRARY, PDF, ADD, BACK, SETTINGS, MICROPHONE, WORKSPACE, EXPLORER, ASSISTANT, REVISION, LEARNING, BOOKMARK }
object IconRegistry {
    val all = IconId.entries.associateWith { it.name.lowercase().replaceFirstChar(Char::titlecase) }
}

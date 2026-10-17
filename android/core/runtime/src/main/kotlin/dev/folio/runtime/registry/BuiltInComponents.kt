package dev.folio.runtime.registry

import dev.folio.model.ui.*

object BuiltInComponents {
    fun registry() = ComponentRegistry(listOf(
        contract(ComponentKind.READER, "Reading canvas", 320, 700,
            setOf(ComponentAction.SELECT, ComponentAction.ANNOTATE, ComponentAction.NAVIGATE), setOf(GestureId.TAP, GestureId.NATIVE_SELECTION, GestureId.SCROLL),
            "Source, sentence, scroll, explicit reading confirmations/progress, exact native bookmarks and separate source-linked notes/highlights/underlines"),
        contract(ComponentKind.QUIZ, "Question practice", 300, 520,
            setOf(ComponentAction.SELECT, ComponentAction.SUBMIT, ComponentAction.SUSPEND, ComponentAction.RESUME, ComponentAction.NAVIGATE),
            setOf(GestureId.TAP), "Current attempt/index, practice timer, answers, confidence, checks/actors, immutable saved questions/keys and read-only review selection"),
        contract(ComponentKind.RECALL, "Active recall", 300, 520,
            setOf(ComponentAction.SUBMIT, ComponentAction.SUSPEND, ComponentAction.RESUME),
            setOf(GestureId.TAP, GestureId.SCROLL), "Source references, private answer draft, reveal, reported rating and attempt history"),
        contract(ComponentKind.EXPLANATION, "Explanation", 280, 440,
            emptySet(), setOf(GestureId.SCROLL), "Source and explanation text", inline = true),
        contract(ComponentKind.COMPARISON, "Comparison table", 300, 540,
            emptySet(), setOf(GestureId.SCROLL), "Source, headers, rows", inline = true),
        contract(ComponentKind.DIAGRAM, "Source figure", 280, 500,
            setOf(ComponentAction.ZOOM), setOf(GestureId.TRANSFORM), "Source asset, zoom, pan"),
    ))

    private fun contract(kind: ComponentKind, title: String, min: Int, preferred: Int,
        actions: Set<ComponentAction>, gestures: Set<GestureId>, state: String, inline: Boolean = false) =
        ComponentContract(kind, title, min, preferred,
            setOf(Presentation.SURFACE, Presentation.DOMINANT, Presentation.CAPSULE) +
                if (inline) setOf(Presentation.INLINE) else emptySet(),
            actions + setOf(ComponentAction.FOCUS, ComponentAction.HIDE, ComponentAction.PIN),
            gestures, setOf(MotionId.FADE, MotionId.EXPAND, MotionId.SLIDE, MotionId.FOCUS), state,
            "48dp controls, labelled actions, scalable text, reduced-motion transitions")
}

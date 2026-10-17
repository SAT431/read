package dev.folio.runtime.layout

import dev.folio.model.ui.ComponentInstance

data class LayoutGroup(val components: List<ComponentInstance>, val minimumWidthDp: Int, val weight: Float)
data class LayoutColumn(val widthDp: Float, val groups: List<LayoutGroup>)
data class LayoutPlan(val columns: List<LayoutColumn>, val capsules: List<ComponentInstance>, val compact: Boolean)

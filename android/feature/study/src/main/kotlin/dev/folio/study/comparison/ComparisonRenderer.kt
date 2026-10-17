package dev.folio.study.comparison

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.model.ui.ComponentKind
import dev.folio.ui.component.*

class ComparisonRenderer : RegisteredRenderer {
    override val kind = ComponentKind.COMPARISON
    @Composable override fun Render(context: ComponentRenderContext) {
        val table = context.activity.data as ActivityData.Comparison
        Text("Source-linked comparison")
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row { table.headers.forEach { Text(it, Modifier.width(150.dp).padding(12.dp), style = MaterialTheme.typography.titleSmall) } }
            table.rows.forEach { row ->
                HorizontalDivider()
                Row { row.forEach { Text(it, Modifier.width(150.dp).padding(12.dp)) } }
            }
        }
    }
}

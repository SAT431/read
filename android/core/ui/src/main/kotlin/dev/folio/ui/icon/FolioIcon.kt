package dev.folio.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FolioIcon(id: IconId, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.size(40.dp).testTag("icon-${id.name.lowercase()}"), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (id in setOf(IconId.PHYSICS, IconId.CHEMISTRY, IconId.BOTANY, IconId.ZOOLOGY)) {
            Canvas(Modifier.size(32.dp)) { when (id) {
                IconId.PHYSICS -> physicsIcon(color); IconId.CHEMISTRY -> chemistryIcon(color)
                IconId.BOTANY -> botanyIcon(color); IconId.ZOOLOGY -> zoologyIcon(color); else -> Unit
            } }
        } else UtilityIcon(id)
    }
}

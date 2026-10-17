package dev.folio.library.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import dev.folio.designsystem.theme.LocalReducedMotion
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import dev.folio.model.workspace.AppDestination
import dev.folio.ui.control.SemanticButton
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.CurriculumCatalog

@Composable
fun HomeScreen(catalog: CurriculumCatalog, pdfs: List<LibraryPdf>, dispatch: (AppCommand) -> Unit) {
    val reduced = LocalReducedMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val reveal by animateFloatAsState(if (entered) 1f else 0f, if (reduced) snap() else tween(260), label = "dashboard-reveal")
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 12.dp.toPx() }
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR SPACE TO THINK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Make room for curiosity.", style = MaterialTheme.typography.headlineLarge)
            }
        }
        HomeStudyCard(pdfs, dispatch)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your subjects", style = MaterialTheme.typography.titleLarge)
                Text("Four paths. One goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SemanticButton("home-search", "Search study", AppCommand.Navigate(AppDestination.SEARCH), dispatch)
        }
        SubjectGrid(catalog, pdfs, dispatch)
        RecentDocuments(pdfs, dispatch)
        Text("${catalog.baseline} · ${catalog.chapters().size} chapters · ${pdfs.size} saved PDFs",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

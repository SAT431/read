package dev.folio.explorer.catalog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.designsystem.primitive.FolioSurface
import dev.folio.model.ui.ComponentContract

@Composable
internal fun ContractCard(contract: ComponentContract, onAdd: () -> Unit) {
    FolioSurface {
        Text(contract.title, style = MaterialTheme.typography.titleMedium)
        Text("Minimum ${contract.minimumWidthDp}dp · preferred ${contract.preferredWidthDp}dp")
        Text("Presentations: ${contract.presentations.joinToString()}")
        Text("Actions: ${contract.actions.joinToString()}")
        Text("Gestures: ${contract.gestures.joinToString()}")
        Text("Motion: ${contract.motions.joinToString()}")
        Text("Saved: ${contract.persistedState}")
        Text(contract.accessibility)
        FolioActionButton("Add ${contract.title}", onAdd)
    }
}

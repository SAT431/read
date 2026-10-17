package dev.folio.designsystem.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.folio.designsystem.token.FolioTokens

@Composable
fun FolioSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(FolioTokens.surfaceRadius),
        border = BorderStroke(androidx.compose.ui.unit.Dp(1f), MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(FolioTokens.gutter), verticalArrangement = Arrangement.spacedBy(FolioTokens.gutter), content = content)
    }
}

package dev.folio.library.pdf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.ui.control.*

@Composable
internal fun PdfPageSelector(pdfId: String, pageIndex: Int, pageCount: Int, dispatch: (AppCommand) -> Unit) {
    var input by rememberSaveable(pdfId, pageIndex) { mutableStateOf((pageIndex + 1).toString()) }
    val target = input.toIntOrNull()?.minus(1)
    val valid = target != null && target in 0 until pageCount
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it.take(6).filter(Char::isDigit) }, Modifier.width(120.dp)
            .semanticControl("pdf-page-input", AppCommand.PdfPage(target ?: pageIndex)),
            label = { Text("File page") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        SemanticButton("pdf-page-go", "Go", AppCommand.PdfPage(target ?: pageIndex), dispatch, enabled = valid)
    }
}

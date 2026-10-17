package com.example.read

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.read.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Android file-picker results enter here; semantic commands can request but cannot fabricate them. */
class LibraryViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutableImporting = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    val importing = mutableImporting.asStateFlow()
    val error = mutableError.asStateFlow()
    fun importPdf(uri: Uri, chapterId: String, title: String) = viewModelScope.launch {
        if (mutableImporting.value) return@launch
        mutableImporting.value = true; mutableError.value = null
        try {
            require(graph.curriculum.chapters().any { it.id == chapterId })
            graph.importer.import(uri, chapterId, title)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = "PDF import failed: ${error.message}" }
        finally { mutableImporting.value = false }
    }
}

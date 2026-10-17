package com.example.read

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.read.navigation.FolioRoot
import com.example.read.assistant.AssistantViewModel
import com.example.read.engineering.PageEngineeringViewModel
import com.example.read.assistant.VoiceInputViewModel
import dev.folio.local.audio.MicrophoneRecorder

class MainActivity : ComponentActivity() {
    private lateinit var workspaceModel: WorkspaceViewModel
    private lateinit var voiceModel: VoiceInputViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as FolioApplication).graph
        workspaceModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceViewModel(graph) as T
        })[WorkspaceViewModel::class.java]
        lifecycle.addObserver(com.example.read.timer.PracticeTimerLifecycleObserver(graph.session, graph.persistenceScope) { isChangingConfigurations })
        val libraryModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(graph) as T
        })[LibraryViewModel::class.java]
        val assistantModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AssistantViewModel(graph) as T
        })[AssistantViewModel::class.java]
        val engineeringModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PageEngineeringViewModel(graph) as T
        })[PageEngineeringViewModel::class.java]
        voiceModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VoiceInputViewModel(graph, MicrophoneRecorder(applicationContext), assistantModel::ask) as T
        })[VoiceInputViewModel::class.java]
        setContent { FolioRoot(workspaceModel, libraryModel, assistantModel, engineeringModel, voiceModel) }
    }
    override fun onStop() {
        if (voiceModel.state.value.recording || voiceModel.state.value.processing) voiceModel.cancel()
        workspaceModel.flush(); super.onStop()
    }
}

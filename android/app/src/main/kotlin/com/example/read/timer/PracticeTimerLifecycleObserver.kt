package com.example.read.timer

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.folio.runtime.state.WorkspaceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The application-owned main scope preserves stop ordering and survives ViewModel disposal. */
class PracticeTimerLifecycleObserver(private val session: WorkspaceSession, private val scope: CoroutineScope,
    private val changingConfiguration: () -> Boolean = { false }) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) { scope.launch { session.setForeground(true) } }
    override fun onStop(owner: LifecycleOwner) { if (!changingConfiguration()) scope.launch { session.setForeground(false) } }
    override fun onDestroy(owner: LifecycleOwner) { owner.lifecycle.removeObserver(this) }
}

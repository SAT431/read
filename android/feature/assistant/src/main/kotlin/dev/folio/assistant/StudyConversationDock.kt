package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.assistant.*
import dev.folio.model.command.AppCommand
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.ui.control.SemanticButton

/** A quiet persistent entry point. Conversation occupies space only when explicitly opened. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyConversationDock(pdfId: String, state: AssistantState, voice: VoiceInputState, connected: Boolean,
    plan: DocumentStudyPlan?, readerReady: Boolean, lineLabel: String?, onAsk: (String) -> Boolean,
    onCancel: () -> Unit, dispatch: (AppCommand) -> Unit) {
    var expanded by rememberSaveable(pdfId) { mutableStateOf(false) }
    var draft by rememberSaveable(pdfId) { mutableStateOf("") }
    val replyId=state.messages.lastOrNull{it.role==MessageRole.ASSISTANT}?.id
    var seenReply by rememberSaveable(pdfId){mutableStateOf(replyId)}
    Surface(shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp),tonalElevation=2.dp,
        color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("study-companion")) {
        Column(Modifier.padding(horizontal=16.dp,vertical=4.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.Center) {
                TextButton({expanded=true},Modifier.testTag("study-chat-toggle")) { Text("✧ Ask Folio") }
                SemanticButton("study-speak",if(voice.recording) "Stop listening" else "Speak",
                    if(voice.recording) AppCommand.FinishVoice else AppCommand.StartVoice,dispatch,
                    enabled=voice.recording || (connected && !state.busy && !voice.processing))
                if(plan != null && readerReady) {
                    if(plan.guided && plan.sentenceId != null) {
                        SemanticButton("study-finished-line","Finished · next",AppCommand.FinishStudyLine(pdfId,plan.version,requireNotNull(plan.sentenceId)),dispatch)
                        SemanticButton("study-pause-guide","Pause",AppCommand.GuideStudy(pdfId,plan.version,false),dispatch)
                    } else SemanticButton("study-start-guide","Guide me",AppCommand.GuideStudy(pdfId,plan.version,true),dispatch)
                }
                if(voice.recording || voice.processing) SemanticButton("study-cancel-voice","Cancel voice",AppCommand.CancelVoice,dispatch)
                if(state.busy) TextButton(onCancel,enabled=state.status != AssistantStatus.EXECUTING) { Text("Cancel request") }
            }
            val status = when {
                voice.recording -> "Listening · pause to send"
                voice.processing -> "Transcribing…"
                state.busy -> "Working on your request…"
                voice.error != null || state.error != null -> "Request needs attention · open Ask Folio"
                plan?.guided == true -> "${lineLabel.orEmpty()} · Finished the highlighted line?"
                replyId!=null && replyId!=seenReply -> "Folio replied · open Ask Folio"
                else -> null
            }
            status?.let { Text(it,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(bottom=4.dp)) }
        }
    }
    if(expanded) StudyChatSheet(state,connected,draft,{draft=it},onAsk={ if(onAsk(draft.trim())) draft="" },
        onClose={expanded=false;seenReply=replyId},onCancel=onCancel,error=voice.error ?: state.error)
}

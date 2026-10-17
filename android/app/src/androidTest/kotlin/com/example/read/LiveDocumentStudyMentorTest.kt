package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.assistant.*
import dev.folio.model.command.AppCommand
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.remote.DirectGeminiMentor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real mentor/source calls on an isolated generated PDF; never invents learner read progress. */
class LiveDocumentStudyMentorTest {
    @Test fun mentorSelectsPreparesAndAnswersFromSelectedOriginalWithoutConfirmingReading() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val base = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(base).load())
        val f = PreparationDeviceFixture(base, CoroutineScope(SupervisorJob() + Dispatchers.Default), live = true)
        try {
            f.seed()
            f.outlines.save(DocumentOutline(f.pdf.document.id,f.checksum,2,listOf(0,1),listOf(
                DocumentTopic("amber","Amber grouping","Amber sentence and original figure",0,0,hasFigures=true),
                DocumentTopic("other","Other page","Unrelated control page",1,1))))
            f.act(AppCommand.StudyPdf(f.pdf.id))
            val prompt = listOf("gemini-orchestrator.md","native-tool-payloads.md").joinToString("\n") {
                base.assets.open(it).bufferedReader().use { reader -> reader.readText() }
            }
            val mentor = DirectGeminiMentor(profile,prompt)
            var calls = 0
            val assistant = f.assistant(object : MentorProvider {
                override suspend fun respond(request: MentorRequest): MentorResponse {
                    calls++
                    assertFalse("Private draft leaked",request.capabilityContext.toString().contains("Private preparation draft"))
                    return mentor.respond(request)
                }
            },documentStudy = true)
            assistant.ask("For today's study select only Amber grouping from my current topic map, then begin preparing it in the full-screen reader using the fresh plan version. Do both steps in this request. Do not confirm any reading, change notes, or process Other page.")
            assertNull(assistant.state.value.error,assistant.state.value.error)
            val plan = f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(listOf("amber"),plan.topicIds); assertEquals(listOf(0),plan.pages)
            assertEquals(StudyMode.READING,plan.mode)
            withTimeout(100000) {
                while (f.pages.page(f.pdf.document.id,0) == null && f.studyPreparation.state.value[f.pdf.id]?.error == null) delay(100)
            }
            assertNull(f.studyPreparation.state.value[f.pdf.id]?.error,f.studyPreparation.state.value[f.pdf.id]?.error)
            assertNotNull(f.pages.page(f.pdf.document.id,0))
            assistant.ask("Quote and explain the original sentence about amber circles from my prepared selected topic in your reply. Stay in this full-screen study, keep the topic selection, notes and all reading confirmations unchanged.")
            assertEquals(assistant.state.value.error,AssistantStatus.COMPLETED,assistant.state.value.status)
            val answer = assistant.state.value.messages.last { it.role == MessageRole.ASSISTANT }.text
            assertTrue("Prepared source was not explained",answer.contains("amber",true) && answer.contains("group",true))
            assertTrue("Unexpected mentor request count",calls in 2..5); assertEquals(1,f.engineCalls.get())
            assertNull(f.pages.page(f.pdf.document.id,1))
            assertTrue("Learner data changed",f.original.activities.all { (id,value) -> f.workspace.state.value.activities[id] == value })
            assertTrue(f.workspace.state.value.activities.values.all { (it.data as? dev.folio.model.activity.ActivityData.Reader)?.readingConfirmations.isNullOrEmpty() })
            assertArrayEquals(f.originalBytes,f.originalFile.readBytes())
        } finally { f.close() }
    }
}

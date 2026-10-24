package dev.blobs.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.AppViewModel
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DraftSubmissionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun typingWhileSubmissionIsBeingSavedPreservesTheNextDraft() {
        val container = (compose.activity.application as BlobsApplication).container
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded && model.work.value.ready }
        val previous = runBlocking { container.preferences.options.first() }
        val key = "draft-${UUID.randomUUID()}"
        val fixture = PackageEntity(
            key, key, "1", "model", "Test model", "unused", 1,
            "0".repeat(64), "Test", "local:test", 1
        )
        val starter = fixture.copy(
            key = "$key-starter",
            packageId = model.catalog.starterLibrary.manifest.id,
            kind = "library",
            enabled = false
        )
        val needsStarter = !model.catalog.hasStarterLibrary(model.packages.value)
        var thread = ""
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blocker: Job? = null
        var submission: Job? = null
        try {
            runBlocking {
                container.database.packages().save(fixture)
                if (needsStarter) container.database.packages().save(starter)
                container.preferences.selectModel(key)
                container.preferences.selectScope("$key/missing-source")
                model.newConversation().join()
            }
            thread = model.conversationId.value
            compose.waitUntil(10_000) {
                model.selectedModel()?.key == key &&
                    model.catalog.hasStarterLibrary(model.packages.value) &&
                    model.selectedScope() == setOf("$key/missing-source")
            }
            compose.runOnUiThread { model.draft("First question") }
            compose.waitUntil(10_000) {
                runBlocking {
                    container.database.conversations().find(thread)?.draft == "First question"
                }
            }
            blocker = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                container.database.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        locked.complete(Unit)
                        release.await()
                    }
                }
            }
            runBlocking { withTimeout(10_000) { locked.await() } }
            compose.runOnUiThread {
                submission = model.send()
                model.draft("Keep my next question")
            }
            release.complete(Unit)
            runBlocking { withTimeout(10_000) { requireNotNull(submission).join() } }
            assertEquals("Keep my next question", model.state.value.draft)
            compose.waitUntil(10_000) {
                runBlocking {
                    container.database.conversations().find(thread)?.draft ==
                        "Keep my next question"
                }
            }
        } finally {
            release.complete(Unit)
            runBlocking {
                withTimeout(10_000) {
                    blocker?.join()
                    submission?.join()
                    model.work.first { !it.busy }
                }
                if (thread.isNotBlank()) container.database.conversations().delete(thread)
                container.database.packages().delete(key)
                if (needsStarter) container.database.packages().delete(starter.key)
                container.preferences.selectModel(previous.model)
                container.preferences.selectScope(previous.scope)
                container.preferences.selectConversation(previous.conversation)
            }
        }
    }
}

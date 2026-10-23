package dev.blobs.app.data

import android.content.Context
import android.net.Uri
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.OfflineAnswerNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ConversationExport {
    suspend fun write(
        context: Context,
        destination: Uri,
        conversations: ConversationStore,
        currentWork: () -> WorkState
    ) = withContext(Dispatchers.IO) {
        val output = requireNotNull(context.contentResolver.openOutputStream(destination))
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("# Blobs conversations\n")
            for (item in conversations.all()) {
                writer.appendLine("## ${item.title.ifBlank { "Untitled conversation" }}\n")
                writer.appendLine("Conversation: ${item.id}")
                writer.appendLine("Created: ${item.createdAt}")
                writer.appendLine("Updated: ${item.updatedAt}\n")
                if (item.draft.isNotBlank()) writer.appendLine("Draft: ${item.draft}\n")
                for (turn in conversations.turns(item.id)) {
                    val live = currentWork()
                    val answer = if (live.turnId == turn.id && live.text.isNotBlank()) {
                        live.text
                    } else {
                        turn.answer
                    }
                    val modelAnswer = answer.removePrefix(
                        OfflineAnswerNotice.forQuestion(turn.question)
                    )
                    val sourceData = if (live.turnId == turn.id) live.evidence else turn.evidence
                    writer.appendLine(
                        "### ${turn.question}\n\n$modelAnswer\n\n" +
                            "Turn: ${turn.id}\n" +
                            "Status: ${statusLabel(turn.status)}\n" +
                            "Model: ${turn.model}\n" +
                            "Scope: ${formatScope(turn.scope)}\n" +
                            "Library scope snapshot: " +
                                "${turn.libraryScope.ifBlank { "legacy turn" }}\n" +
                            "Grounding: ${groundingLabel(modelAnswer, sourceData, turn.grounding)}\n" +
                            "Finish: ${finishLabel(turn.finishReason)}\n" +
                            "Elapsed: ${turn.elapsedMs} ms\n" +
                            turn.error.takeIf { it.isNotBlank() }?.let { "Error: $it\n" }
                                .orEmpty()
                    )
                    val sources = Evidence.decodeOrNull(sourceData)
                    if (sources == null) {
                        writer.appendLine(
                            "Unreadable source record (preserved):\n$sourceData\n"
                        )
                    }
                    if (sources?.isEmpty() == true) {
                        writer.appendLine("No local sources returned.\n")
                    }
                    val evidenceItems = sources.orEmpty()
                    val citedIndices = AnswerText.citedSourceIndices(modelAnswer, evidenceItems.size)
                    val cited = evidenceItems.withIndex().filter { indexed ->
                        indexed.index in citedIndices
                    }
                    if (cited.isNotEmpty()) writer.appendLine("Cited local sources:\n")
                    cited.forEach { indexed ->
                        writeEvidence(writer, "[S${indexed.index + 1}]", indexed.value)
                    }
                    val related = evidenceItems.withIndex().filter { indexed ->
                        indexed.index !in citedIndices
                    }
                    if (related.isNotEmpty()) {
                        writer.appendLine("Retrieved evidence not cited by the answer:\n")
                    }
                    related.forEach { indexed ->
                        writeEvidence(writer, "Retrieved evidence", indexed.value)
                    }
                }
            }
        }
    }

    private fun writeEvidence(
        writer: java.io.BufferedWriter,
        label: String,
        evidence: Evidence
    ) {
        writer.appendLine(
            "$label: ${evidence.title}\n" +
                "Document: ${evidence.id}\n" +
                "Section: ${evidence.section}\n" +
                "Publisher: ${evidence.publisher}\n" +
                "URL: ${evidence.url}\n" +
                "License: ${evidence.license}\n" +
                "Updated: ${evidence.updated}\n" +
                "Saved: ${evidence.saved}\n" +
                "Edition: ${evidence.edition}\n" +
                evidence.passageId?.let { "Passage: $it\n" }.orEmpty() +
                "\n${evidence.text}\n"
        )
    }

    private fun groundingLabel(
        answer: String,
        encodedEvidence: String,
        storedGrounding: String
    ): String {
        when (TurnGrounding.fromStored(storedGrounding)) {
            TurnGrounding.GROUNDED -> return "grounded"
            TurnGrounding.UNVERIFIED -> return "unverified"
            TurnGrounding.NOT_REQUESTED -> return "not_requested"
            TurnGrounding.NONE -> return "none"
            else -> Unit
        }
        val sourceCount = Evidence.decodeOrNull(encodedEvidence)?.size ?: return "unverified"
        return if (AnswerText.hasCompleteCitationCoverage(answer, sourceCount)) {
            "grounded"
        } else {
            "unverified"
        }
    }

    private fun statusLabel(stored: String): String = when (TurnStatus.fromStored(stored)) {
        TurnStatus.RUNNING -> "running"
        TurnStatus.COMPLETE -> "complete"
        TurnStatus.STOPPED -> "stopped"
        TurnStatus.ERROR -> "error"
        TurnStatus.INTERRUPTED -> "interrupted"
        TurnStatus.LIMITED -> "limited"
        TurnStatus.CONTEXT_FULL -> "context_full"
        TurnStatus.NO_SOURCES -> "no_sources"
        TurnStatus.UNVERIFIED -> "unverified"
        else -> "legacy"
    }

    private fun finishLabel(stored: String): String = when (TurnFinishReason.fromStored(stored)) {
        TurnFinishReason.STOP -> "stop"
        TurnFinishReason.TOKEN_LIMIT -> "token_limit"
        TurnFinishReason.CANCELLED -> "cancelled"
        TurnFinishReason.CONTEXT_FULL -> "context_full"
        TurnFinishReason.ERROR -> "error"
        TurnFinishReason.PENDING -> "pending"
        else -> "legacy"
    }

    private fun formatScope(scope: String): String = when (scope) {
        "*" -> "all enabled libraries"
        else -> scope.lineSequence().filter(String::isNotBlank).joinToString(", ")
            .ifBlank { "no libraries" }
    }
}

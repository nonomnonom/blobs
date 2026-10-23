package dev.blobs.app.data

import android.content.Context
import android.net.Uri
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.inference.ModelSupport
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
                    val response = TurnResponse.of(
                        turn.copy(evidence = sourceData),
                        modelAnswer
                    )
                    writer.appendLine(
                        "### ${response.question}\n\n${response.body}\n\n" +
                            "Turn: ${response.turnId}\n" +
                            "Status: ${statusLabel(response.status)}\n" +
                            "Model: ${response.model}\n" +
                            "Scope: ${formatScope(response.scope)}\n" +
                            "Library scope snapshot: " +
                            "${response.libraryScope.ifBlank { "legacy turn" }}\n" +
                            "Grounding: ${groundingLabel(response.grounding)}\n" +
                            "Model support: ${supportLabel(response.modelSupport)}\n" +
                            "Finish: ${finishLabel(response.finishReason)}\n" +
                            "Usage: ${response.usage.sourceCount} retrieved, " +
                            "${response.usage.citedSourceCount} cited, " +
                            "${response.usage.suggestionCount} follow-ups, " +
                            "${response.usage.elapsedMs} ms\n" +
                            turn.error.takeIf { it.isNotBlank() }?.let { "Error: $it\n" }
                                .orEmpty()
                    )
                    if (response.suggestions.isNotEmpty()) {
                        writer.appendLine("Suggested next questions:\n")
                        response.suggestions.forEach { offer ->
                            val origin = if (offer.sourceIndex >= 0) {
                                " from [S${offer.sourceIndex + 1}]"
                            } else {
                                ""
                            }
                            writer.appendLine("- ${offer.text} (${offer.basis.storedValue}$origin)")
                        }
                        writer.appendLine()
                    }
                    val evidenceItems = response.evidence
                    if (evidenceItems == null) {
                        writer.appendLine(
                            "Unreadable source record (preserved):\n$sourceData\n"
                        )
                    }
                    if (evidenceItems?.isEmpty() == true) {
                        writer.appendLine("No local sources returned.\n")
                    }
                    val citedIndices = response.citedSourceIndices
                    val cited = evidenceItems.orEmpty().withIndex().filter { indexed ->
                        indexed.index in citedIndices
                    }
                    if (cited.isNotEmpty()) writer.appendLine("Cited local sources:\n")
                    cited.forEach { indexed ->
                        writeEvidence(writer, "[S${indexed.index + 1}]", indexed.value)
                    }
                    val related = evidenceItems.orEmpty().withIndex().filter { indexed ->
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

    private fun writeEvidence(writer: java.io.BufferedWriter, label: String, evidence: Evidence) {
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

    private fun groundingLabel(grounding: TurnGrounding): String = when (grounding) {
        TurnGrounding.GROUNDED -> "grounded"
        TurnGrounding.UNVERIFIED -> "unverified"
        TurnGrounding.NOT_REQUESTED -> "not_requested"
        TurnGrounding.NONE -> "none"
        else -> "unverified"
    }

    private fun statusLabel(status: TurnStatus?): String = when (status) {
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

    private fun finishLabel(finishReason: TurnFinishReason?): String = when (finishReason) {
        TurnFinishReason.STOP -> "stop"
        TurnFinishReason.TOKEN_LIMIT -> "token_limit"
        TurnFinishReason.CANCELLED -> "cancelled"
        TurnFinishReason.CONTEXT_FULL -> "context_full"
        TurnFinishReason.ERROR -> "error"
        TurnFinishReason.PENDING -> "pending"
        else -> "legacy"
    }

    private fun supportLabel(claimed: ModelSupport?): String =
        claimed?.storedValue ?: "not recorded"

    private fun formatScope(scope: String): String = when (scope) {
        "*" -> "all enabled libraries"

        else -> scope.lineSequence().filter(String::isNotBlank).joinToString(", ")
            .ifBlank { "no libraries" }
    }
}

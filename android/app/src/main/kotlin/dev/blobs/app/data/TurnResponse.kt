package dev.blobs.app.data

import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.data.local.finishReasonKind
import dev.blobs.app.data.local.groundingKind
import dev.blobs.app.data.local.statusKind
import dev.blobs.app.inference.AnswerGrounding
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.FollowUp
import dev.blobs.app.inference.FollowUpSuggestions
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.inference.OfflineAnswerNotice

/**
 * The durable response shape of one research turn.
 *
 * Lifecycle, answer body, citation annotations, next-question offers, and usage
 * are all projections of data the turn itself carries, so the chat, the exporter,
 * and any later reader agree on one record.
 */
internal data class TurnResponse(
    val turnId: String,
    val question: String,
    val status: TurnStatus?,
    val finishReason: TurnFinishReason?,
    val grounding: TurnGrounding,
    val body: String,
    val evidence: List<Evidence>?,
    val annotations: List<CitationAnnotation>,
    val suggestions: List<FollowUp>,
    val modelSupport: ModelSupport?,
    val usage: TurnUsage,
    val error: String,
    val model: String,
    val scope: String,
    val libraryScope: String
) {
    val hasNoSources: Boolean
        get() = status == TurnStatus.NO_SOURCES ||
            (
                status == TurnStatus.COMPLETE &&
                    scope.isNotBlank() &&
                    evidence?.isEmpty() == true &&
                    body.isNotBlank()
                )

    val citedSourceIndices: Set<Int>
        get() = annotations.mapTo(linkedSetOf()) { it.sourceIndex }

    companion object {
        /** Builds the record for a turn, optionally overlying in-flight work output. */
        fun of(turn: TurnEntity, answer: String = turn.answer): TurnResponse =
            of(turn, answer, Evidence.decodeOrNull(turn.evidence))

        /** Same projection with evidence already resolved, for callers holding live sources. */
        fun of(turn: TurnEntity, answer: String, sources: List<Evidence>?): TurnResponse {
            val stored = answer.removePrefix(OfflineAnswerNotice.forQuestion(turn.question))
            val legacy = AnswerText.extractFollowUps(stored)
            val body = legacy.first
            val annotations = sources?.let { entries ->
                AnswerText.citations(body, entries.size).map { citation ->
                    CitationAnnotation(
                        marker = "S${citation.sourceIndex + 1}",
                        sourceIndex = citation.sourceIndex,
                        range = citation.range,
                        evidence = entries[citation.sourceIndex]
                    )
                }
            }.orEmpty()
            val persisted = FollowUpSuggestions.decodeOrNull(turn.suggestions).orEmpty()
            val suggestions = persisted.ifEmpty { FollowUpSuggestions.legacy(legacy.second) }
            return TurnResponse(
                turnId = turn.id,
                question = turn.question,
                status = turn.statusKind,
                finishReason = turn.finishReasonKind,
                grounding = grounding(turn, body, sources?.size ?: 0),
                body = body,
                evidence = sources,
                annotations = annotations,
                suggestions = suggestions,
                modelSupport = ModelSupport.fromStored(turn.modelSupport),
                usage = TurnUsage(
                    sourceCount = sources?.size ?: 0,
                    citedSourceCount = annotations.map { it.sourceIndex }.distinct().size,
                    suggestionCount = suggestions.size,
                    elapsedMs = turn.elapsedMs
                ),
                error = turn.error,
                model = turn.model,
                scope = turn.scope,
                libraryScope = turn.libraryScope
            )
        }

        private fun grounding(turn: TurnEntity, body: String, sourceCount: Int): TurnGrounding =
            when (turn.groundingKind) {
                TurnGrounding.GROUNDED -> TurnGrounding.GROUNDED

                TurnGrounding.UNVERIFIED -> TurnGrounding.UNVERIFIED

                TurnGrounding.NOT_REQUESTED -> TurnGrounding.NOT_REQUESTED

                else -> AnswerGrounding.decide(
                    body,
                    sourceCount,
                    ModelSupport.fromStored(turn.modelSupport)
                )
            }
    }
}

/** A marker actually visible in the answer body, bound to this turn's own evidence. */
internal data class CitationAnnotation(
    val marker: String,
    val sourceIndex: Int,
    val range: IntRange,
    val evidence: Evidence
) {
    val title: String get() = evidence.title
}

/** Volume accounting for one turn, from measurements the turn itself carries. */
internal data class TurnUsage(
    val sourceCount: Int,
    val citedSourceCount: Int,
    val suggestionCount: Int,
    val elapsedMs: Long
)

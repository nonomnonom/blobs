package dev.blobs.app.inference

import dev.blobs.app.data.local.TurnGrounding

/**
 * Decides the source-grounding disclosure for one finished turn.
 *
 * Structural citation coverage plus a content match against the cited passages is the only route to
 * `grounded`: every sentence has to carry a marker *and* mention something the passage it cites
 * mentions, because a marker alone can dress up a claim its source never makes. A support claim from
 * the model can make that stricter but can never grant it, so a self-declared "partial" or
 * "unsourced" answer stays unverified even when every sentence happens to carry a marker. A missing
 * claim, written by an older build, leaves the structural decision alone.
 */
internal object AnswerGrounding {
    fun decide(body: String, sources: List<String>, claimed: ModelSupport?): TurnGrounding = when {
        body.isBlank() -> TurnGrounding.NONE

        claimed == ModelSupport.UNSOURCED || claimed == ModelSupport.PARTIAL ->
            TurnGrounding.UNVERIFIED

        sources.isNotEmpty() &&
            AnswerText.hasCompleteCitationCoverage(body, sources.size) &&
            AnswerText.citationsMatchTheirPassages(body, sources) -> TurnGrounding.GROUNDED

        else -> TurnGrounding.UNVERIFIED
    }
}

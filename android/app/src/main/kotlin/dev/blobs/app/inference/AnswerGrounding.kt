package dev.blobs.app.inference

import dev.blobs.app.data.local.TurnGrounding

/**
 * Decides the source-grounding disclosure for one finished turn.
 *
 * Structural citation coverage is the only route to `grounded`. A support claim from the model can
 * make that stricter but can never grant it, so a self-declared "partial" or "unsourced" answer
 * stays unverified even when every sentence happens to carry a marker. A missing claim, written by
 * an older build, leaves the structural decision alone.
 */
internal object AnswerGrounding {
    fun decide(body: String, sourceCount: Int, claimed: ModelSupport?): TurnGrounding = when {
        body.isBlank() -> TurnGrounding.NONE

        claimed == ModelSupport.UNSOURCED || claimed == ModelSupport.PARTIAL ->
            TurnGrounding.UNVERIFIED

        sourceCount > 0 && AnswerText.hasCompleteCitationCoverage(body, sourceCount) ->
            TurnGrounding.GROUNDED

        else -> TurnGrounding.UNVERIFIED
    }
}

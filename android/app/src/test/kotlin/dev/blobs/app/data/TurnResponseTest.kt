package dev.blobs.app.data

import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.inference.FollowUp
import dev.blobs.app.inference.FollowUpSuggestions
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.inference.OfflineAnswerNotice
import dev.blobs.app.inference.SuggestionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnResponseTest {
    @Test
    fun modelWrittenFollowUpBlockLeavesTheBodyAndBecomesLegacyOffers() {
        val raw = "Turn the pile weekly. [S1]\n\nFollow-up questions:\n" +
            "1. What heats the pile?\n2. How long does curing take?"
        val response = projection(turn(raw), listOf(source("Heating phases")))

        assertEquals("Turn the pile weekly. [S1]", response.body)
        assertEquals(
            listOf(
                FollowUp("What heats the pile?", SuggestionBasis.LEGACY_MODEL),
                FollowUp("How long does curing take?", SuggestionBasis.LEGACY_MODEL)
            ),
            response.suggestions
        )
        assertEquals(2, response.usage.suggestionCount)
    }

    @Test
    fun persistedOffersWinOverALegacyBlock() {
        val stored = FollowUpSuggestions.encode(
            listOf(FollowUp("Heating phases", SuggestionBasis.UNEXPLORED_SOURCE, 0))
        )
        val raw = "Turn the pile weekly. [S1]\n\nFollow-up questions:\n1. What heats the pile?"
        val response = projection(
            turn(raw, suggestions = stored),
            listOf(source("Heating phases"))
        )

        assertEquals(
            listOf(FollowUp("Heating phases", SuggestionBasis.UNEXPLORED_SOURCE, 0)),
            response.suggestions
        )
    }

    @Test
    fun annotationsBindEveryVisibleMarkerToThisTurnsEvidence() {
        val sources = listOf(source("Heating"), source("Curing"))
        val response = projection(turn("Alpha [S1], beta [S2], again [S1]."), sources)

        assertEquals(listOf("S1", "S2", "S1"), response.annotations.map { it.marker })
        assertEquals(listOf("Heating", "Curing", "Heating"), response.annotations.map { it.title })
        assertEquals(sources[0], response.annotations.first().evidence)
        assertEquals(setOf(0, 1), response.citedSourceIndices)
        assertEquals(2, response.usage.citedSourceCount)
        assertEquals(2, response.usage.sourceCount)
    }

    @Test
    fun storedGroundingDecidesBeforeReDerivation() {
        val response = projection(
            turn(
                answer = "Compost heats without a marker.",
                grounding = TurnGrounding.GROUNDED.storedValue
            ),
            listOf(source("Heating"))
        )

        assertEquals(TurnGrounding.GROUNDED, response.grounding)
    }

    @Test
    fun legacyRowsRecomputeGroundingFromTheirOwnText() {
        val sources = listOf(source("Heating"))
        val covered = projection(turn("Compost heats in stages. [S1]"), sources)
        val uncovered = projection(turn("Compost heats in stages plainly."), sources)
        val unanswered = projection(turn(""), sources)

        assertEquals(TurnGrounding.GROUNDED, covered.grounding)
        assertEquals(TurnGrounding.UNVERIFIED, uncovered.grounding)
        assertEquals(TurnGrounding.NONE, unanswered.grounding)
    }

    @Test
    fun aWeakSupportClaimCannotOutrankCompleteCoverage() {
        val sources = listOf(source("Heating"))
        val partial = projection(
            turn("Compost heats in stages. [S1]", support = ModelSupport.PARTIAL.storedValue),
            sources
        )
        val unsourced = projection(
            turn("Compost heats in stages. [S1]", support = ModelSupport.UNSOURCED.storedValue),
            sources
        )
        val sourced = projection(
            turn("Compost heats in stages. [S1]", support = ModelSupport.SOURCED.storedValue),
            sources
        )

        assertEquals(ModelSupport.PARTIAL, partial.modelSupport)
        assertEquals(TurnGrounding.UNVERIFIED, partial.grounding)
        assertEquals(TurnGrounding.UNVERIFIED, unsourced.grounding)
        assertEquals(TurnGrounding.GROUNDED, sourced.grounding)
    }

    @Test
    fun completeCoverageOverAnUnrelatedPassageIsNotGrounded() {
        val railGuide = source(
            "Rail",
            text = "Rail travel in South Korea runs express services between the islands."
        )
        val response = projection(
            turn(
                "The 1945 Constitution of Indonesia does not detail any emergency powers. [S1]",
                support = ModelSupport.SOURCED.storedValue
            ),
            listOf(railGuide)
        )

        assertEquals(TurnGrounding.UNVERIFIED, response.grounding)
    }

    @Test
    fun lifecycleFieldsProjectToTheirOwnVocabulary() {
        val response = projection(
            turn(
                answer = "Compost heats in stages. [S1]",
                status = TurnStatus.STOPPED.storedValue,
                finish = TurnFinishReason.CANCELLED.storedValue
            ),
            listOf(source("Heating"))
        )

        assertEquals(TurnStatus.STOPPED, response.status)
        assertEquals(TurnFinishReason.CANCELLED, response.finishReason)
        assertEquals(2_400L, response.usage.elapsedMs)
        assertEquals("turn-1", response.turnId)
    }

    @Test
    fun anUnreadableLifecycleStaysUnknown() {
        val response = projection(
            turn(answer = "Plain answer.", status = "MYSTERY", finish = "MYSTERY"),
            emptyList()
        )

        assertNull(response.status)
        assertNull(response.finishReason)
    }

    @Test
    fun noSourcesIsOnlyClaimedForTheRightTurns() {
        val legacyStatus = projection(
            turn("Nothing matched.", status = TurnStatus.NO_SOURCES.storedValue),
            emptyList()
        )
        val scopedEmpty = projection(
            turn("Nothing matched.", scope = "en-wikipedia"),
            emptyList()
        )
        val unscoped = projection(turn("Nothing matched."), emptyList())

        assertTrue(legacyStatus.hasNoSources)
        assertTrue(scopedEmpty.hasNoSources)
        assertFalse(unscoped.hasNoSources)
    }

    @Test
    fun theOfflineNoticeIsNotPartOfTheAnswerBody() {
        val question = "Is the museum open now?"
        val notice = OfflineAnswerNotice.forQuestion(question)
        assertTrue(notice.isNotBlank())

        val response = projection(
            turn(notice + "It closed in 1998.", question = question),
            emptyList()
        )

        assertEquals("It closed in 1998.", response.body)
    }

    private fun projection(turn: TurnEntity, sources: List<Evidence>): TurnResponse =
        TurnResponse.of(turn, turn.answer, sources)

    private fun turn(
        answer: String,
        question: String = "How does composting work?",
        status: String = TurnStatus.COMPLETE.storedValue,
        grounding: String = TurnGrounding.LEGACY.storedValue,
        finish: String = TurnFinishReason.LEGACY.storedValue,
        suggestions: String = "",
        scope: String = "",
        support: String = ""
    ) = TurnEntity(
        id = "turn-1",
        conversationId = "chat-1",
        question = question,
        answer = answer,
        status = status,
        model = "test-model",
        scope = scope,
        evidence = "not decoded by these tests",
        createdAt = 1_700_000_000_000L,
        elapsedMs = 2_400L,
        grounding = grounding,
        finishReason = finish,
        suggestions = suggestions,
        modelSupport = support
    )

    private fun source(section: String, text: String = "Compost heats in stages.") = Evidence(
        id = "reference/$section",
        title = section,
        publisher = "Reference",
        url = "local:composting",
        license = "CC0",
        updated = "2026-01-01",
        saved = "2026-01-02",
        section = section,
        text = text,
        edition = "test"
    )
}

package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpSuggestionsTest {
    @Test
    fun sameInputsProduceTheSameOffers() {
        val sources = listOf(
            source(section = "Soil water", text = "Infiltration rates\nSand drains quickly."),
            source(section = "Root depth")
        )
        val question = "How do trees drink?"
        val answer = "Leaves release vapour. [S1]"

        assertEquals(
            FollowUpSuggestions.from(question, answer, sources),
            FollowUpSuggestions.from(question, answer, sources)
        )
    }

    @Test
    fun uncitedSourceOffersItsOwnSectionVerbatim() {
        val sources = listOf(
            source(section = "Transpiration", text = "Leaves release vapour."),
            source(section = "Root depth", title = "Roots")
        )
        val offers = FollowUpSuggestions.from(
            "How do trees drink?",
            "Leaves release vapour. [S1]",
            sources
        )

        assertEquals(listOf(FollowUp("Root depth", SuggestionBasis.UNEXPLORED_SOURCE, 1)), offers)
    }

    @Test
    fun sourceWithoutSectionFallsBackToItsTitle() {
        val offers = FollowUpSuggestions.from(
            "Explain the treaty",
            "The agreement was signed in 1809.",
            listOf(source(section = "", title = "Border agreements"))
        )

        assertEquals(
            listOf(FollowUp("Border agreements", SuggestionBasis.UNEXPLORED_SOURCE, 0)),
            offers
        )
    }

    @Test
    fun citedSourceOffersAHeadingTheAnswerNeverReached() {
        val sources = listOf(
            source(
                section = "Canopy",
                text = "Interception dynamics\nThe canopy holds rainfall."
            )
        )
        val offers = FollowUpSuggestions.from(
            "Why are forests damp?",
            "Leaf litter stays wet. [S1]",
            sources
        )

        assertEquals(
            listOf(FollowUp("Interception dynamics", SuggestionBasis.UNCOVERED_HEADING, 0)),
            offers
        )
    }

    @Test
    fun headingAlreadyCoveredByTheAnswerIsNotOfferedAgain() {
        val sources = listOf(
            source(section = "Canopy", text = "Interception dynamics\nThe canopy holds rainfall.")
        )
        val offers = FollowUpSuggestions.from(
            "What is interception dynamics?",
            "Interception dynamics is canopy storage. [S1]",
            sources
        )

        assertTrue(offers.isEmpty())
    }

    @Test
    fun candidatesRepeatingTheQuestionOrEachOtherAreDropped() {
        val sources = listOf(
            source(section = "Canopy storage", text = "Measured in millimetres of water."),
            source(section = "root DEPTH"),
            source(section = "Stem growth"),
            source(section = "Stem   growth"),
            source(section = "Leaf scab")
        )
        val offers = FollowUpSuggestions.from(
            "Root depth",
            "Shallow roots hold the tree. [S1]",
            sources
        )

        assertEquals(
            listOf(
                FollowUp("Stem growth", SuggestionBasis.UNEXPLORED_SOURCE, 2),
                FollowUp("Leaf scab", SuggestionBasis.UNEXPLORED_SOURCE, 4)
            ),
            offers
        )
    }

    @Test
    fun offersStayWithinTheCap() {
        val sources = (1..5).map { index -> source(section = "Chapter number $index") }
        val offers = FollowUpSuggestions.from("Outline", "Preface. [S1]", sources)

        assertEquals(FollowUpSuggestions.MAX, offers.size)
        assertEquals(
            listOf("Chapter number 2", "Chapter number 3", "Chapter number 4"),
            offers.map { it.text }
        )
    }

    @Test
    fun unusableCandidatesAreRejected() {
        val sources = listOf(
            source(section = "Tiny"),
            source(section = "a".repeat(121)),
            source(section = "Pipe | separated"),
            source(section = "Bell\u0007tone"),
            source(section = "!!! ???"),
            source(section = "Deep   roots")
        )
        val offers = FollowUpSuggestions.from("What about soil?", "Clay holds water.", sources)

        assertEquals(listOf(FollowUp("Deep roots", SuggestionBasis.UNEXPLORED_SOURCE, 5)), offers)
    }

    @Test
    fun noOffersWithoutAnAnswerOrWithoutEvidence() {
        val blankAnswer = FollowUpSuggestions.from(
            "Anything",
            "   ",
            listOf(source(section = "Root depth"))
        )
        assertTrue(blankAnswer.isEmpty())
        val withoutEvidence = FollowUpSuggestions.from(
            "Anything",
            "An answer. [S1]",
            emptyList()
        )
        assertTrue(withoutEvidence.isEmpty())
    }

    @Test
    fun persistedRecordsRoundTrip() {
        val sources = listOf(
            source(section = "Soil water", text = "Infiltration rates"),
            source(section = "Root depth")
        )
        val offers = FollowUpSuggestions.from(
            "How do trees drink?",
            "Warm air pulls water. [S1]",
            sources
        )
        val stored = FollowUpSuggestions.encode(offers)

        assertEquals(offers, FollowUpSuggestions.decodeOrNull(stored))
    }

    @Test
    fun modelWrittenOffersAreNeverPersistedAsAppOffers() {
        val legacy = listOf(FollowUp("Ask about roots", SuggestionBasis.LEGACY_MODEL))

        assertEquals("", FollowUpSuggestions.encode(legacy))
        assertTrue(FollowUpSuggestions.decodeOrNull("").orEmpty().isEmpty())
    }

    @Test
    fun malformedRecordsDecodeToNull() {
        listOf(
            "unexplored_source|0",
            "unexplored_source|0|extra|here",
            "invented_basis|0|Ask about roots",
            "legacy_model|0|Ask about roots",
            "unexplored_source|abc|Ask about roots",
            "unexplored_source|-1|Ask about roots",
            "unexplored_source|0|tiny",
            "unexplored_source|0|Pipe | separated"
        ).forEach { record ->
            assertNull(record, FollowUpSuggestions.decodeOrNull(record))
        }
    }

    @Test
    fun legacyBodiesBecomeUnlinkedOffers() {
        val parsed = listOf(
            "  Ask about   roots ",
            "hi",
            "Bell\u0007tone",
            "0123456789".repeat(13)
        )

        assertEquals(
            listOf(FollowUp("Ask about roots", SuggestionBasis.LEGACY_MODEL)),
            FollowUpSuggestions.legacy(parsed)
        )
    }

    private fun source(section: String = "", title: String = "Untitled", text: String = "") =
        Evidence(
            id = "reference/${section.ifBlank { title }}",
            title = title,
            publisher = "Reference",
            url = "local:test",
            license = "CC0",
            updated = "2026-01-01",
            saved = "2026-01-02",
            section = section,
            text = text,
            edition = "test"
        )
}

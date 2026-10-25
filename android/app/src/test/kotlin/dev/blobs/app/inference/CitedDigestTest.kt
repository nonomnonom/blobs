package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CitedDigestTest {
    private fun evidence(index: Int, text: String, title: String = "Source $index") = Evidence(
        id = "reference/source-$index",
        title = title,
        publisher = "Reference",
        url = "local:source-$index",
        license = "CC0",
        updated = "2026-01-01",
        saved = "2026-01-02",
        section = "Section $index",
        text = text,
        edition = "test"
    )

    @Test
    fun everyLineCarriesItsOwnCitation() {
        val digest = CitedDigest.build(
            listOf(
                evidence(1, "Bangkok is served by two airports and a dense bus network."),
                evidence(2, "Hanoi's old quarter is compact enough to cross on foot.")
            )
        )

        assertEquals(
            "- Bangkok is served by two airports and a dense bus network. [S1]\n" +
                "- Hanoi's old quarter is compact enough to cross on foot. [S2]",
            digest
        )
        assertTrue(AnswerText.hasCompleteCitationCoverage(digest.orEmpty(), 2))
    }

    @Test
    fun headingsAndFragmentsAreSkipped() {
        val digest = CitedDigest.build(
            listOf(
                evidence(
                    1,
                    "Get around\n\n" +
                        "The Skytrain is the fastest way across the city during rush hour."
                )
            )
        )

        assertEquals(
            "- The Skytrain is the fastest way across the city during rush hour. [S1]",
            digest
        )
    }

    @Test
    fun duplicatePassagesKeepOneLineAndSourcesStayNumbered() {
        val text = "Tokyo Station connects the city to the national Shinkansen network."
        val digest = CitedDigest.build(
            listOf(
                evidence(1, text),
                evidence(2, text),
                evidence(3, "A Japan Rail Pass covers most long-distance journeys.")
            )
        )

        assertEquals(
            "- Tokyo Station connects the city to the national Shinkansen network. [S1]\n" +
                "- A Japan Rail Pass covers most long-distance journeys. [S3]",
            digest
        )
    }

    @Test
    fun emptyOrUnusableEvidenceProducesNothing() {
        assertNull(CitedDigest.build(emptyList()))
        assertNull(CitedDigest.build(listOf(evidence(1, "\n\n"), evidence(2, "Too short."))))
    }

    @Test
    fun alreadyCitedSourcesAreNotRepeated() {
        val digest = CitedDigest.build(
            listOf(
                evidence(1, "Bangkok's elevated train avoids the worst of the traffic."),
                evidence(2, "Hanoi's old quarter is compact enough to cross on foot."),
                evidence(3, "Both cities are walkable in the early morning.")
            ),
            skipIndices = setOf(1)
        )

        assertEquals(
            "- Hanoi's old quarter is compact enough to cross on foot. [S2]\n" +
                "- Both cities are walkable in the early morning. [S3]",
            digest
        )
    }

    @Test
    fun requestedLineCountCapsTheDigest() {
        val digest = CitedDigest.build(
            (1..4).map { index ->
                evidence(index, "Passage $index describes how the route works in practice today.")
            },
            maximumLines = 2
        )

        assertEquals(
            "- Passage 1 describes how the route works in practice today. [S1]\n" +
                "- Passage 2 describes how the route works in practice today. [S2]",
            digest
        )
    }

    @Test
    fun aTurnThatUsedNoneOfItsSourcesIsReplaced() {
        val sources = listOf(
            evidence(1, "Bangkok sprawls across high-rise districts and heavy traffic."),
            evidence(2, "Hanoi's old quarter is compact enough to cross on foot in a morning.")
        )

        val gated = CitedDigest.gate("Bangkok is a large and busy city.", sources)

        assertEquals(CitedDigest.build(sources), gated)
    }

    @Test
    fun aOneSidedComparisonGainsTheSubjectsItDropped() {
        val sources = listOf(
            evidence(1, "Bangkok sprawls across high-rise districts and heavy traffic."),
            evidence(2, "Hanoi's old quarter is compact enough to cross on foot in a morning."),
            evidence(3, "Southeast Asia rewards a first visitor with short hops between capitals."),
            evidence(4, "Ramkhamhaeng sits on the eastern bank of the Chao Phraya river.")
        )
        val prose = "Bangkok sprawls across high-rise districts and heavy traffic [S1]."

        val gated = CitedDigest.gate(prose, sources)

        assertEquals(
            "$prose\n\n" +
                "- Hanoi's old quarter is compact enough to cross on foot in a morning. [S2]\n" +
                "- Southeast Asia rewards a first visitor with short hops between capitals. [S3]",
            gated
        )
        assertTrue(AnswerText.hasCompleteCitationCoverage(gated.orEmpty(), 3))
    }

    @Test
    fun theTopUpSkipsTheSubjectTheAnswerAlreadyUsed() {
        val sources = listOf(
            evidence(
                1,
                "Bangkok sprawls across high-rise districts and heavy traffic.",
                "Bangkok"
            ),
            evidence(
                2,
                "Hanoi's old quarter is compact enough to cross on foot in a morning.",
                "Hanoi"
            ),
            evidence(
                3,
                "Southeast Asia rewards a first visitor with short hops between capitals.",
                "Southeast Asia"
            ),
            evidence(
                4,
                "Ramkhamhaeng sits on the eastern bank of the Chao Phraya river.",
                "Bangkok/Ramkhamhaeng"
            )
        )
        val prose = "Bangkok sprawls across high-rise districts and heavy traffic [S4]."

        val gated = CitedDigest.gate(prose, sources)

        assertEquals(
            "$prose\n\n" +
                "- Hanoi's old quarter is compact enough to cross on foot in a morning. [S2]\n" +
                "- Southeast Asia rewards a first visitor with short hops between capitals. [S3]",
            gated
        )
    }

    @Test
    fun anAnswerThatAlreadyUsesTwoSourcesStands() {
        val sources = listOf(
            evidence(1, "Bangkok sprawls across high-rise districts and heavy traffic."),
            evidence(2, "Hanoi's old quarter is compact enough to cross on foot in a morning."),
            evidence(3, "Southeast Asia rewards a first visitor with short hops between capitals.")
        )
        val prose = "Bangkok sprawls [S1]. Hanoi is walkable [S2]."

        assertNull(CitedDigest.gate(prose, sources))
    }

    @Test
    fun anAnswerWithNoOtherSubjectToNameStands() {
        val sources = listOf(
            evidence(
                1,
                "Silom fills with office workers at lunch and empties after dark.",
                "Bangkok/Silom"
            ),
            evidence(
                2,
                "Thonburi lies on the west bank of the river, away from the traffic.",
                "Bangkok/Thonburi"
            ),
            evidence(
                3,
                "Khao San Road draws budget travellers with cheap rooms and street food.",
                "Bangkok/Khao San Road"
            )
        )

        assertNull(CitedDigest.gate("Silom is busy at lunch [S1].", sources))
    }

    @Test
    fun aThinEvidenceSetIsNotPadded() {
        val sources = listOf(
            evidence(1, "Bangkok sprawls across high-rise districts and heavy traffic."),
            evidence(2, "Hanoi's old quarter is compact enough to cross on foot in a morning.")
        )

        assertNull(CitedDigest.gate("Bangkok sprawls across high-rise districts [S1].", sources))
        assertNull(CitedDigest.gate("Bangkok sprawls.", emptyList()))
    }

    @Test
    fun navigationListsAreNotTreatedAsProse() {
        val digest = CitedDigest.build(
            listOf(
                evidence(
                    1,
                    "Rail travel in Asia and Oceania articles Australia • China • India • " +
                        "Japan • Laos • New Zealand"
                ),
                evidence(2, "Japan's rail network connects major cities with frequent departures.")
            )
        )

        assertEquals(
            "- Japan's rail network connects major cities with frequent departures. [S2]",
            digest
        )
    }

    @Test
    fun aLongPassageIsTrimmedAtAWordBoundary() {
        val digest = CitedDigest.build(
            listOf(
                evidence(
                    1,
                    "Rail travel in Japan is fast and frequent, and the national network " +
                        "connects nearly every major city with frequent departures throughout " +
                        "the day, although rural lines run less often."
                )
            )
        )

        val line = digest.orEmpty()
        assertTrue(line, line.endsWith(" [S1]"))
        assertTrue(line, line.length < 300)
        assertTrue(line, !line.contains("  "))
    }
}

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

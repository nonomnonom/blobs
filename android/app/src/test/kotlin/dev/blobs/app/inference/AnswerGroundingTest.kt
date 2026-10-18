package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGroundingTest {
    @Test
    fun acceptsClaimsWithMeaningfulOverlapAndRejectsUnrelatedCitations() {
        val checksum = evidence(
            title = "Checksum",
            text =
            "A checksum detects errors in data transmission or storage and verifies data integrity."
        )
        val glassTube = evidence(
            title = "Glass tube",
            text = "Glass tubes are cylindrical hollow-wares used in laboratory glassware."
        )

        assertTrue(
            AnswerGrounding.hasSupportedCitations(
                "A checksum detects transmission errors and verifies data integrity [S1].",
                listOf(checksum)
            )
        )
        assertFalse(
            AnswerGrounding.hasSupportedCitations(
                "Water rises in narrow glass tubes because surface tension and adhesion pull it upward [S1].",
                listOf(glassTube)
            )
        )
        assertFalse(
            AnswerGrounding.hasSupportedCitations(
                "The tank volume decreases because inflow is lower than outflow [S1].",
                listOf(
                    evidence("Revolutions per minute", "A measure of rotational speed per minute.")
                )
            )
        )
    }

    @Test
    fun requiresAtLeastOneUsableNamespacedCitation() {
        val source = evidence("Checksum", "A checksum detects errors in data transmission.")
        assertFalse(
            AnswerGrounding.hasSupportedCitations("A checksum detects errors.", listOf(source))
        )
        assertFalse(
            AnswerGrounding.hasSupportedCitations("A checksum detects errors [S2].", listOf(source))
        )
        assertFalse(AnswerGrounding.hasSupportedCitations("[S1]", listOf(source)))
    }

    @Test
    fun combinesPassagesCitedForOneComparisonClaim() {
        val tcp =
            evidence("Transmission Control Protocol", "TCP provides reliable, ordered delivery.")
        val udp = evidence("User Datagram Protocol", "UDP does not guarantee delivery or ordering.")
        assertTrue(
            AnswerGrounding.hasSupportedCitations(
                "TCP provides ordered delivery, while UDP does not guarantee it [S1][S2].",
                listOf(tcp, udp)
            )
        )
    }

    @Test
    fun acceptsGroupedCitationsAndUsesEveryReferencedSource() {
        val tcp =
            evidence("Transmission Control Protocol", "TCP provides reliable, ordered delivery.")
        val udp =
            evidence(
                "User Datagram Protocol",
                "UDP sends datagrams and does not guarantee delivery."
            )

        assertTrue(
            AnswerGrounding.hasSupportedCitations(
                "UDP sends datagrams without guaranteed delivery [S1, S2].",
                listOf(tcp, udp)
            )
        )
    }

    private fun evidence(title: String, text: String) = Evidence(
        id = "library/document",
        title = title,
        publisher = "Publisher",
        url = "https://example.test/source",
        license = "Test",
        updated = "2026-10-18",
        saved = "2026-10-18",
        section = title,
        text = text,
        edition = "Test edition"
    )
}

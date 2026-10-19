package dev.blobs.app

import dev.blobs.app.data.library.Evidence
import dev.blobs.app.inference.AnswerText
import org.junit.Assert.assertEquals
import org.junit.Test

class CitationProvenanceTest {
    @Test
    fun savedCitationIndicesResolveToTheOriginalRetrievedSources() {
        val retrieved = listOf(
            evidence("nps/hike-smart", "Hike Smart", "https://nps.gov/hike"),
            evidence("nps/water-hazards", "Water Hazards", "https://nps.gov/water")
        )
        val saved = Evidence.decode(Evidence.encode(retrieved))
        val answer = AnswerText.validSourceCitations(
            "Currents can be hard to see [S2]. Plan the hike before leaving [S1].",
            saved.size
        )
        val citedSourceIds = AnswerText.citations(answer, saved.size)
            .map { saved[it.sourceIndex].id }

        assertEquals(retrieved, saved)
        assertEquals(listOf("nps/water-hazards", "nps/hike-smart"), citedSourceIds)
    }

    private fun evidence(id: String, title: String, url: String) = Evidence(
        id = id,
        title = title,
        publisher = "National Park Service",
        url = url,
        license = "U.S. Government work",
        updated = "2024-05-03",
        saved = "2026-10-19",
        section = title,
        text = "Source text for $title",
        edition = "2026-10.2"
    )
}

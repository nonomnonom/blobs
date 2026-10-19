package dev.blobs.app.inference

import dev.blobs.app.data.library.RetrievalQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalQueryTest {
    @Test
    fun aNewTopicDoesNotInheritThePreviousQuestion() {
        val question = "Why did the Western Roman Empire fall?"
        assertEquals(question, RetrievalQuery.resolve(question, "How do trees cool cities?"))
        assertEquals(question, RetrievalQuery.resolve(question, null))
    }

    @Test
    fun explicitFollowUpsRetainTheReferencedTopic() {
        val previous = "How do trees cool cities?"
        for (question in listOf(
            "Why?",
            "How do they do that?",
            "What about green roofs?",
            "Tell me more"
        )) {
            assertTrue(RetrievalQuery.resolve(question, previous).endsWith(previous))
        }
    }

    @Test
    fun shortEntitiesSurviveWithoutAcceptingFtsOperators() {
        assertEquals("\"ai\" OR \"eu\" OR \"co2\"", RetrievalQuery.match("AI OR EU AND CO2 NOT *"))
    }

    @Test
    fun adjacentContentPhrasesAddSpecificityWithoutRemovingRecallTerms() {
        assertEquals(
            "\"trees\" OR \"reduce\" OR \"urban\" OR \"heat\" OR " +
                "\"trees reduce\" OR \"reduce urban\" OR \"urban heat\"",
            RetrievalQuery.match("How do trees reduce urban heat?")
        )
    }

    @Test
    fun genericGuideAndListWordingDoesNotBecomeRetrievalContent() {
        assertEquals(
            "\"lightweight\" OR \"emergency\" OR \"shelter\" OR \"options\" OR " +
                "\"lightweight emergency\" OR \"emergency shelter\" OR \"shelter options\"",
            RetrievalQuery.match(
                "What lightweight emergency shelter options does the guide list?"
            )
        )
    }

    @Test
    fun anInstructionVerbDoesNotCreateUnrelatedEvidenceForAnUnknownSubject() {
        assertEquals(
            "\"zqxvblobsunobtainium\"",
            RetrievalQuery.match("Describe zqxvblobsunobtainium.")
        )
    }

    @Test
    fun broadPlaceQuestionsCanPrioritizeThePlaceGuideWithoutOverridingSubtopics() {
        assertTrue(
            RetrievalQuery.isBroadTitleQuestion(
                "What should I know before visiting Jakarta?",
                "Jakarta"
            )
        )
        assertTrue(
            !RetrievalQuery.isBroadTitleQuestion(
                "How can I travel around Japan by rail?",
                "Japan"
            )
        )
        assertTrue(
            !RetrievalQuery.isBroadTitleQuestion(
                "What should I pack for a beach trip?",
                "Beach"
            )
        )
    }

    @Test
    fun titleOverlapRecognizesReorderedTopicWordsAndKeepsSpecificTitlesDistinct() {
        val travelTerms = RetrievalQuery.titleTerms("What are the basics of travel?")
        assertEquals(2, RetrievalQuery.titleOverlap(travelTerms, "Travel basics"))
        val bangkokTerms = RetrievalQuery.titleTerms(
            "How do I get from the airport to Bangkok city?"
        )
        assertEquals(1, RetrievalQuery.titleOverlap(bangkokTerms, "Bangkok"))
        assertEquals(0, RetrievalQuery.titleOverlap(bangkokTerms, "Chao Phraya Basin"))
        val packingTerms = RetrievalQuery.titleTerms("What should I pack for a beach trip?")
        assertEquals(2, RetrievalQuery.titleOverlap(packingTerms, "Packing for beach travel"))
        assertEquals(1, RetrievalQuery.titleOverlap(packingTerms, "Beach"))
    }
}

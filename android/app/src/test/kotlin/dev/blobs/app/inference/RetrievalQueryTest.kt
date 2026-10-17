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
    fun anInstructionVerbDoesNotCreateUnrelatedEvidenceForAnUnknownSubject() {
        assertEquals(
            "\"zqxvblobsunobtainium\"",
            RetrievalQuery.match("Describe zqxvblobsunobtainium.")
        )
    }
}

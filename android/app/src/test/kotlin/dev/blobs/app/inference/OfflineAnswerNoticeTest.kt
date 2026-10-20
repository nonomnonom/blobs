package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAnswerNoticeTest {
    @Test
    fun explicitLiveRequestsAreQualifiedAsSavedReferenceMaterial() {
        for (question in listOf(
            "Which vegan restaurant in Ubud is open right now and has the best current reviews?",
            "Is this museum currently open?",
            "Give me current opening hours and ratings.",
            "What are the latest train fares?",
            "What are the current prices?",
            "Is the hotel currently available?",
            "What is the weather today?",
            "Which shops are OPEN\nRIGHT NOW?"
        )) {
            val notice = OfflineAnswerNotice.forQuestion(question)
            assertTrue(question, notice.contains("can't verify live"))
            assertTrue(question, notice.contains("individual entries can be older"))
        }
    }

    @Test
    fun historicalAndConceptualQuestionsDoNotReceiveALiveDataNotice() {
        for (question in listOf(
            "What were the opening hours in 2023?",
            "Explain the current rating of an electrical wire.",
            "How do ocean currents move?",
            "Compare local trains and limited express services.",
            "What do reviews tell us about a restaurant?"
        )) {
            assertEquals(question, "", OfflineAnswerNotice.forQuestion(question))
        }
    }
}

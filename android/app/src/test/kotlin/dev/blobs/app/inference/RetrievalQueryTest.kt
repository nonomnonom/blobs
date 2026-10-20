package dev.blobs.app.inference

import dev.blobs.app.data.library.RetrievalQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalQueryTest {
    @Test
    fun standaloneResponseLengthInstructionsStayOutOfSearchContent() {
        val question = "What is a solar panel?"
        assertEquals(
            question,
            RetrievalQuery.forSearch("$question Please answer in two short sentences.")
        )
        assertEquals(
            "Why does wind turn a turbine?",
            RetrievalQuery.forSearch("Respond in 3 sentences: Why does wind turn a turbine?")
        )
        assertEquals(
            "$question What is a wind turbine?",
            RetrievalQuery.forSearch(
                "$question Please answer in two short sentences. " +
                    "What is a wind turbine? Please answer in two short sentences."
            )
        )
    }

    @Test
    fun sentenceTopicsAndQuotedInstructionsRemainSearchable() {
        for (question in listOf(
            "What are consecutive life sentences?",
            "What makes short sentences easy to read?",
            "What does 'answer in two short sentences' mean?",
            "Explain the is-a relationship in object-oriented programming."
        )) {
            assertEquals(question, RetrievalQuery.forSearch(question))
        }
    }

    @Test
    fun articlesAloneCannotMakeGenericPhrasesIntoNamedSubjects() {
        assertTrue(RetrievalQuery.names("What is a solar panel?").none { it.key == "is a" })
        assertTrue(RetrievalQuery.names("What is a solar panel?").any { it.key == "solar panel" })
        assertTrue(
            RetrievalQuery.names("Tell me about A Star Is Born.").any {
                it.key ==
                    "a star is born"
            }
        )
    }

    @Test
    fun aNewTopicDoesNotInheritThePreviousQuestion() {
        val question = "Why did the Western Roman Empire fall?"
        assertEquals(
            question,
            RetrievalQuery.resolve(question, listOf("How do trees cool cities?"))
        )
        assertEquals(question, RetrievalQuery.resolve(question, emptyList()))
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
            assertTrue(RetrievalQuery.resolve(question, listOf(previous)).endsWith(previous))
        }
    }

    @Test
    fun choicesAndOrdinalFollowUpsKeepTheirSourceTopic() {
        val previous = "Compare the vegan restaurants in Ubud and Bangkok."
        for (question in listOf(
            "Which of those has more vegan options?",
            "Which one is cheaper?",
            "How does the second one compare?",
            "What about the first option?"
        )) {
            assertEquals("$question $previous", RetrievalQuery.resolve(question, listOf(previous)))
        }
        val newTopic = "Which cities have the best public transport?"
        assertEquals(newTopic, RetrievalQuery.resolve(newTopic, listOf(previous)))
    }

    @Test
    fun chainedFollowUpsRetainTheNamedAnchor() {
        val history = listOf(
            "Tell me about vegan restaurants in Ubud.",
            "Which of those are cheapest?",
            "How do they compare?"
        )
        val resolved = RetrievalQuery.resolve("Why?", history)
        assertEquals("Why? ${history.joinToString(" ")}", resolved)
        val reset = history + "How do trees cool cities?" + "How do they do that?"
        val next = RetrievalQuery.resolve("Tell me more", reset)
        assertTrue(next.contains("trees"))
        assertTrue(!next.contains("Ubud"))
        assertEquals("Why?", RetrievalQuery.resolve("Why?", emptyList()))
        val longContext = RetrievalQuery.resolve("Why?", listOf("x".repeat(1000), "Tell me more"))
        assertTrue(longContext.length <= 620)
    }

    @Test
    fun aSubjectIntroducedInAFollowUpSurvivesLaterFollowUps() {
        val history = listOf(
            "Tell me about vegan restaurants in Ubud.",
            "What about Bangkok?",
            "Which of those is cheaper?",
            "Tell me more"
        )
        val resolved = RetrievalQuery.resolve("Why?", history)
        assertTrue(resolved.contains("Ubud"))
        assertTrue(resolved.contains("Bangkok"))
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
    fun directTitleMatchDoesNotPromoteOneGenericWordOverMoreSpecificSources() {
        val question = "How should I store food and behave around wildlife at a campsite?"
        assertTrue(!RetrievalQuery.isDirectTitleMatch(question, "Wildlife"))
        assertTrue(RetrievalQuery.isDirectTitleMatch(question, "Pian Upe Wildlife Reserve"))
        assertTrue(
            RetrievalQuery.isDirectTitleMatch(
                "How can I get from the airport to Bangkok city?",
                "Bangkok"
            )
        )
        assertTrue(RetrievalQuery.isDirectTitleMatch("What is wildlife?", "Wildlife"))
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

    @Test
    fun evidenceOverlapRanksSpecificContentAcrossSeparateLibraries() {
        val question = "How should I store food and behave around wildlife at a campsite?"
        val duringStay = RetrievalQuery.evidenceOverlap(
            RetrievalQuery.titleTerms(question),
            "Camping safely",
            "Food and wildlife",
            "Store food safely and never approach or feed wildlife at your campsite."
        )
        val beforeTrip = RetrievalQuery.evidenceOverlap(
            RetrievalQuery.titleTerms(question),
            "Camping safely",
            "Before the trip",
            "Check campground rules, campsite conditions, and weather before leaving."
        )
        assertTrue(duringStay > beforeTrip)
    }
}

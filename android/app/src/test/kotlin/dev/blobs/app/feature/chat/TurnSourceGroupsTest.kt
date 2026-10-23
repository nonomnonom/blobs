package dev.blobs.app.feature.chat

import dev.blobs.app.data.library.Evidence
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnSourceGroupsTest {
    @Test
    fun groupsDuplicatePassagesByLocalIdentityInsteadOfUrl() {
        val original = evidence(
            id = "library/article",
            passageId = 4L,
            url = "https://example.test/article"
        )
        val duplicate = original.copy(url = "https://mirror.test/article")
        val sameUrlDifferentPassage = original.copy(passageId = 5L)

        val groups = groupTurnSources(listOf(original, duplicate, sameUrlDifferentPassage))

        assertEquals(2, groups.size)
        assertEquals(setOf(0, 1), groups[0].sourceIndices)
        assertEquals(1, groups[0].displayNumber)
        assertEquals(setOf(2), groups[1].sourceIndices)
        assertEquals(2, groups[1].displayNumber)
    }

    private fun evidence(id: String, passageId: Long, url: String) = Evidence(
        id = id,
        title = "Example",
        publisher = "Example publisher",
        url = url,
        license = "Example license",
        updated = "2026-10-23",
        saved = "2026-10-23",
        section = "Section",
        text = "Passage text",
        edition = "edition-digest",
        passageId = passageId
    )
}

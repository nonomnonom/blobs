package dev.blobs.app.feature.chat

import dev.blobs.app.data.library.Evidence

internal data class TurnSourceGroup(
    val sourceIndices: Set<Int>,
    val displayNumber: Int,
    val evidence: Evidence
)

private data class TurnSourceIdentity(
    val id: String,
    val edition: String,
    val passageId: Long?,
    val sectionWithoutPassage: String?
)

internal fun groupTurnSources(sources: List<Evidence>): List<TurnSourceGroup> {
    val indicesByIdentity = linkedMapOf<TurnSourceIdentity, MutableList<Int>>()
    sources.forEachIndexed { index, source ->
        val identity = TurnSourceIdentity(
            id = source.id,
            edition = source.edition,
            passageId = source.passageId,
            sectionWithoutPassage = source.section.takeIf { source.passageId == null }
        )
        indicesByIdentity.getOrPut(identity) { mutableListOf() }.add(index)
    }
    return indicesByIdentity.values.mapIndexed { index, sourceIndices ->
        TurnSourceGroup(
            sourceIndices = sourceIndices.toSet(),
            displayNumber = index + 1,
            evidence = sources[sourceIndices.first()]
        )
    }
}

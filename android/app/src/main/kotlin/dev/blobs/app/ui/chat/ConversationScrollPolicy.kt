package dev.blobs.app.ui.chat

/**
 * Where the reader sits relative to the live edge of a transcript that keeps growing.
 *
 * The reader's own movement is the only thing that can take the transcript out of
 * [LiveEdgeMode.FOLLOWING_BOTTOM]; a streamed chunk, a new turn, or a layout change never does.
 */
internal enum class LiveEdgeMode {
    /** The tail of the newest reply is followed while it streams. */
    FOLLOWING_BOTTOM,

    /** The newest turn is pinned near the top and its reply is allowed to grow below it. */
    ANCHORED_TO_TURN,

    /** The reader moved on their own, so the transcript holds still. */
    HELD
}

/** What the transcript should do next, decided without touching any scroll container. */
internal sealed interface ScrollDirective {
    /** Leave the reader exactly where they are. */
    data object None : ScrollDirective

    /** Place the turn at [index] near the top, keeping [peekPx] of the previous turn above it. */
    data class AnchorTurn(val index: Int, val peekPx: Int) : ScrollDirective

    /** Move to the newest tail of the transcript. */
    data object FollowBottom : ScrollDirective
}

/**
 * The scroll rules a research transcript needs: move only when the reader asked to move,
 * start a new turn near the top so it can be read from its beginning, let the answer grow
 * into the room below it, and hand the live edge back on an explicit jump.
 *
 * Opening a saved conversation and sending a new question both place the newest turn, so
 * both arrive through [turnAppeared]. A thread never reopens at the raw bottom, which
 * would drop the reader into an answer without the question that earned it.
 */
internal class ConversationScrollPolicy(private val peekPx: Int) {
    var mode: LiveEdgeMode = LiveEdgeMode.HELD
        private set

    /** The newest turn is now [index]: anchor it so its answer has somewhere to grow. */
    fun turnAppeared(index: Int): ScrollDirective {
        mode = LiveEdgeMode.ANCHORED_TO_TURN
        return ScrollDirective.AnchorTurn(index, peekPx)
    }

    /** Text, evidence, or images arrived. Follow only while the reader is following. */
    fun contentGrew(atLiveEdge: Boolean): ScrollDirective {
        if (mode != LiveEdgeMode.FOLLOWING_BOTTOM || atLiveEdge) return ScrollDirective.None
        return ScrollDirective.FollowBottom
    }

    /**
     * The reader dragged or flung the transcript. Reaching the tail re-engages follow,
     * moving away from it releases it; nothing here scrolls on its own.
     */
    fun readerMoved(atLiveEdge: Boolean): ScrollDirective {
        mode = if (atLiveEdge) LiveEdgeMode.FOLLOWING_BOTTOM else LiveEdgeMode.HELD
        return ScrollDirective.None
    }

    /** An explicit "latest" request brings the reader back and resumes following. */
    fun jumpToLatest(): ScrollDirective {
        mode = LiveEdgeMode.FOLLOWING_BOTTOM
        return ScrollDirective.FollowBottom
    }
}

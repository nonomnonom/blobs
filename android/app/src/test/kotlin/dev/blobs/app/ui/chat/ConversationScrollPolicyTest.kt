package dev.blobs.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationScrollPolicyTest {
    private val peek = 56
    private val policy = ConversationScrollPolicy(peek)

    @Test
    fun aNewestTurnIsAnchoredNearTheTopWithAPeekOfThePreviousTurn() {
        assertEquals(
            ScrollDirective.AnchorTurn(index = 5, peekPx = peek),
            policy.turnAppeared(index = 5)
        )
        assertEquals(LiveEdgeMode.ANCHORED_TO_TURN, policy.mode)
    }

    @Test
    fun streamingNeverFollowsUntilTheReaderAsks() {
        policy.turnAppeared(index = 4)
        assertEquals(ScrollDirective.None, policy.contentGrew(atLiveEdge = false))
    }

    @Test
    fun anAnswerGrowingBelowTheAnchorLeavesTheReaderInPlace() {
        policy.turnAppeared(index = 0)
        repeat(4) { assertEquals(ScrollDirective.None, policy.contentGrew(atLiveEdge = false)) }
        assertEquals(LiveEdgeMode.ANCHORED_TO_TURN, policy.mode)
    }

    @Test
    fun followingTheBottomTracksGrowthUntilItReachesTheTail() {
        policy.jumpToLatest()
        assertEquals(ScrollDirective.FollowBottom, policy.contentGrew(atLiveEdge = false))
        assertEquals(ScrollDirective.None, policy.contentGrew(atLiveEdge = true))
        assertEquals(LiveEdgeMode.FOLLOWING_BOTTOM, policy.mode)
    }

    @Test
    fun scrollingAwayReleasesFollowAndKeepsTheReaderInPlace() {
        policy.jumpToLatest()
        assertEquals(ScrollDirective.None, policy.readerMoved(atLiveEdge = false))
        assertEquals(LiveEdgeMode.HELD, policy.mode)
        assertEquals(ScrollDirective.None, policy.contentGrew(atLiveEdge = false))
    }

    @Test
    fun draggingBackToTheTailReEngagesFollow() {
        policy.turnAppeared(index = 2)
        assertEquals(ScrollDirective.None, policy.readerMoved(atLiveEdge = true))
        assertEquals(LiveEdgeMode.FOLLOWING_BOTTOM, policy.mode)
        assertEquals(ScrollDirective.FollowBottom, policy.contentGrew(atLiveEdge = false))
    }

    @Test
    fun jumpToLatestReturnsAndResumesFollowing() {
        policy.turnAppeared(index = 3)
        policy.readerMoved(atLiveEdge = false)
        assertEquals(ScrollDirective.FollowBottom, policy.jumpToLatest())
        assertEquals(LiveEdgeMode.FOLLOWING_BOTTOM, policy.mode)
        assertEquals(ScrollDirective.FollowBottom, policy.contentGrew(atLiveEdge = false))
    }

    @Test
    fun theTailOfAFinishedTurnIsFollowedOnlyForAFollowingReader() {
        policy.jumpToLatest()
        assertEquals(ScrollDirective.FollowBottom, policy.contentGrew(atLiveEdge = false))
        policy.readerMoved(atLiveEdge = false)
        assertEquals(ScrollDirective.None, policy.contentGrew(atLiveEdge = false))
    }
}

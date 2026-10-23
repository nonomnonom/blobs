package dev.blobs.app.feature.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.chat.ConversationScrollPolicy
import dev.blobs.app.ui.chat.ScrollDirective
import dev.blobs.app.ui.theme.BlobsDimens
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: AppUiState,
    turns: List<TurnEntity>,
    work: WorkState,
    model: AppViewModel,
    modifier: Modifier = Modifier
) {
    val currentThread by model.conversationId.collectAsStateWithLifecycle()
    val ready = model.selectedModel() != null
    val large = LocalDensity.current.fontScale >= 1.5f
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val peekPx = with(LocalDensity.current) { BlobsDimens.conversationTurnPeek.toPx().toInt() }
    val policy = remember(peekPx) { ConversationScrollPolicy(peekPx) }
    val heldByReader = remember { mutableIntStateOf(0) }
    val showLatest by remember { derivedStateOf { list.canScrollForward && turns.isNotEmpty() } }
    var seenTurn by rememberSaveable(currentThread) { mutableStateOf<String?>(null) }

    /**
     * The reader, not the stream, owns the scroll. Only a drag or a fling that we did not
     * start ourselves counts as the reader moving, so following the tail never looks like
     * the reader asked to be there.
     */
    val intent = remember(list, heldByReader, policy) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && heldByReader.intValue == 0) {
                    policy.readerMoved(atLiveEdge = !list.canScrollForward)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (heldByReader.intValue == 0) {
                    policy.readerMoved(atLiveEdge = !list.canScrollForward)
                }
                return Velocity.Zero
            }
        }
    }

    suspend fun follow(directive: ScrollDirective) {
        val last = turns.lastIndex
        if (last < 0) return
        heldByReader.intValue += 1
        try {
            when (directive) {
                ScrollDirective.None -> Unit

                ScrollDirective.FollowBottom -> {
                    list.scrollToItem(last)
                    val tail = list.layoutInfo.visibleItemsInfo.lastOrNull()?.size ?: 0
                    list.scrollBy(tail.toFloat())
                }

                is ScrollDirective.AnchorTurn -> {
                    list.scrollToItem(directive.index.coerceIn(0, last))
                    list.scrollBy(-directive.peekPx.toFloat())
                }
            }
        } finally {
            heldByReader.intValue -= 1
        }
    }

    LaunchedEffect(currentThread, turns.lastOrNull()?.id) {
        val newest = turns.lastOrNull()?.id
        if (newest == null || newest == seenTurn) return@LaunchedEffect
        seenTurn = newest
        follow(policy.turnAppeared(turns.lastIndex))
        if (work.busy) focus.clearFocus()
    }
    LaunchedEffect(work.text, work.evidence, work.busy) {
        follow(policy.contentGrew(atLiveEdge = !list.canScrollForward))
    }

    if (turns.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = BlobsDimens.homeReadingWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                HomeContent(state, work, ready, model)
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        if (work.busy &&
            work.threadId.isNotBlank() &&
            work.threadId != currentThread
        ) {
            Body(R.string.other_answer)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_VIEWPORT)
                    .nestedScroll(intent),
                state = list,
                contentPadding = PaddingValues(
                    top = BlobsDimens.sm,
                    bottom = BlobsDimens.lg
                ),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
            ) {
                items(turns, key = { it.id }) { turn ->
                    TurnCard(
                        if (work.turnId == turn.id) turn.copy(evidence = work.evidence) else turn,
                        if (work.turnId == turn.id &&
                            work.text.isNotBlank()
                        ) {
                            work.text
                        } else {
                            turn.answer
                        },
                        work.busy,
                        model,
                        researchStage = work.researchStage.takeIf { work.turnId == turn.id },
                        latest = turn.id == turns.lastOrNull()?.id,
                        modifier = Modifier.testTag(TAG_ITEM)
                    )
                }
                if (large) {
                    item(key = "follow-up-composer") {
                        Column(
                            Modifier.padding(horizontal = BlobsDimens.page),
                            verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
                        ) {
                            Text(
                                stringResource(R.string.follow_up),
                                style = MaterialTheme.typography.titleMedium
                            )
                            AnswerDisclaimer()
                            Composer(state, work, true, model)
                        }
                    }
                }
            }
            if (showLatest) {
                ElevatedButton(
                    onClick = { scope.launch { follow(policy.jumpToLatest()) } },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BlobsDimens.xs)
                        .heightIn(min = 36.dp)
                        .testTag(TAG_LATEST_BUTTON),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 3.dp
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(18.dp)
                    )
                    Text(
                        stringResource(R.string.latest),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!large) {
                AnswerDisclaimer(Modifier.padding(horizontal = BlobsDimens.page))
                Composer(
                    state,
                    work,
                    true,
                    model,
                    Modifier.padding(
                        horizontal = BlobsDimens.md,
                        vertical = BlobsDimens.xs
                    )
                )
            }
        }
    }
}

private const val TAG_VIEWPORT = "message-scroller-viewport"
private const val TAG_ITEM = "message-scroller-item"
private const val TAG_LATEST_BUTTON = "message-scroller-button"

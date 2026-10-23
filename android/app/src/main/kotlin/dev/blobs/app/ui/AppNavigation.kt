package dev.blobs.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.ui.theme.BlobsComponentShapes
import dev.blobs.app.ui.theme.BlobsDimens
import kotlinx.coroutines.launch

@Composable
internal fun AppNavigation(
    state: AppUiState,
    conversations: List<ConversationEntity>,
    thread: String,
    model: AppViewModel,
    content: @Composable (() -> Unit) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lessMotion = LocalLessMotion.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.page) {
        if (state.page != Page.CHAT) drawer.snapTo(DrawerValue.Closed)
    }
    fun closeThen(action: () -> Unit) {
        scope.launch {
            if (lessMotion) drawer.snapTo(DrawerValue.Closed) else drawer.close()
            action()
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = state.loaded && state.options.welcomed && state.page == Page.CHAT,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = BlobsDimens.drawerWidth).fillMaxHeight(),
                drawerShape = BlobsComponentShapes.drawer,
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    Modifier.fillMaxHeight().padding(horizontal = BlobsDimens.md).then(
                        if (drawer.isClosed && drawer.targetValue == DrawerValue.Closed) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        }
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_wordmark),
                            Modifier.weight(1f).padding(12.dp),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        GlyphButton(R.drawable.ic_close, R.string.close_navigation) {
                            scope.launch {
                                if (lessMotion) {
                                    drawer.snapTo(
                                        DrawerValue.Closed
                                    )
                                } else {
                                    drawer.close()
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        DrawerEntry(
                            label = { Text(stringResource(R.string.new_chat)) },
                            icon = { Icon(painterResource(R.drawable.ic_edit_square), null) },
                            selected = conversations.find {
                                it.id == thread
                            }?.title.isNullOrBlank(),
                            onClick = { closeThen { model.newConversation() } }
                        )
                        DrawerEntry(
                            label = { Text(stringResource(R.string.search_conversations)) },
                            icon = { Icon(painterResource(R.drawable.ic_search), null) },
                            selected = false,
                            onClick = { closeThen { model.navigate(Page.HISTORY) } }
                        )
                        DrawerEntry(
                            label = { Text(stringResource(R.string.library)) },
                            icon = { Icon(painterResource(R.drawable.ic_auto_stories), null) },
                            selected = false,
                            onClick = { closeThen { model.navigate(Page.LIBRARY) } }
                        )
                        Text(
                            stringResource(R.string.recent_conversations),
                            Modifier.padding(start = 12.dp, top = 28.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val recent = conversations.filter {
                            it.title.isNotBlank() ||
                                it.draft.isNotBlank()
                        }
                            .take(6)
                        if (recent.isEmpty()) {
                            Text(
                                stringResource(R.string.recent_empty),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        recent.forEach { item ->
                            DrawerEntry(
                                label = {
                                    Text(
                                        item.title.ifBlank {
                                            item.draft
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                selected = item.id == thread,
                                onClick = { closeThen { model.openConversation(item) } }
                            )
                        }
                    }
                    HorizontalDivider()
                    DrawerEntry(
                        label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(painterResource(R.drawable.ic_settings), null) },
                        selected = false,
                        onClick = { closeThen { model.navigate(Page.SETTINGS) } }
                    )
                }
            }
        }
    ) {
        content {
            focus.clearFocus(force = true)
            keyboard?.hide()
            scope.launch { if (lessMotion) drawer.snapTo(DrawerValue.Open) else drawer.open() }
        }
    }
}

@Composable
internal fun AppHeader(
    state: AppUiState,
    home: Boolean,
    model: AppViewModel,
    openNavigation: () -> Unit
) {
    val reviewing = state.importUri != null
    val chat = state.page == Page.CHAT && !reviewing
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (chat) {
            GlyphButton(R.drawable.ic_menu, R.string.open_navigation, onClick = openNavigation)
        } else {
            GlyphButton(R.drawable.ic_arrow_back, R.string.back) {
                when {
                    reviewing -> model.dismissImport()
                    state.page == Page.IMPORT -> model.finishImport()
                    else -> model.back()
                }
            }
        }
        Text(
            stringResource(
                when {
                    reviewing -> R.string.review_package
                    chat && home -> R.string.app_wordmark
                    state.page == Page.SETUP -> R.string.setup_header
                    else -> pageTitle(state.page)
                }
            ),
            Modifier.weight(1f),
            style = if (chat &&
                home
            ) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chat && !home) {
            TextButton(onClick = { model.newConversation() }) {
                Icon(painterResource(R.drawable.ic_edit_square), null, Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.new_chat))
            }
        }
    }
}

@Composable
private fun DrawerEntry(
    label: @Composable () -> Unit,
    icon: @Composable () -> Unit = {},
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(
                horizontal = BlobsDimens.md,
                vertical = BlobsDimens.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BlobsDimens.md)
        ) {
            icon()
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { label() }
            }
        }
    }
}

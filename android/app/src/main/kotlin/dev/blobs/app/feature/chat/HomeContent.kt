package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Mascot
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel

@Composable
internal fun HomeContent(
    state: ProductState,
    work: WorkState,
    ready: Boolean,
    model: ProductViewModel
) {
    var focused by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column(
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
        if (large) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Mascot(R.drawable.mascot_curious, Modifier.size(80.dp), focused = focused)
                HomeTitle(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeTitle(Modifier.weight(1f))
                Mascot(R.drawable.mascot_curious, Modifier.size(104.dp), focused = focused)
            }
        }
        Composer(state, work, false, model, onFocusChange = { focused = it })
        if (ready) {
            Column {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.home_questions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.offline_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                listOf(R.string.question_printing, R.string.question_heat).forEachIndexed {
                        index,
                        questionId
                    ->
                    val question = stringResource(questionId)
                    Surface(onClick = {
                        model.send(question)
                    }, enabled = !work.busy, color = Color.Transparent) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                question,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                painterResource(R.drawable.ic_arrow_forward),
                                null,
                                Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index == 0) HorizontalDivider()
                }
                Text(
                    stringResource(R.string.home_sources_hint),
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                stringResource(R.string.setup_missing),
                style = MaterialTheme.typography.titleLarge
            )
            Body(R.string.setup_missing_body)
            PrimaryAction(R.string.setup_library) { model.navigate(Page.SETUP) }
        }
    }
}

@Composable
private fun HomeTitle(modifier: Modifier) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    Text(
        stringResource(R.string.home_title),
        modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge.let {
            if (large) it.copy(fontSize = 22.sp, lineHeight = 27.sp) else it
        }
    )
}

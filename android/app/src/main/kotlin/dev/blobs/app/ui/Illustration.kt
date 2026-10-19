package dev.blobs.app.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blobs.app.R

internal val LocalLessMotion = compositionLocalOf { false }

@Composable
internal fun systemLessMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
    var reduced by remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = disabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
internal fun ExperienceCanvas(
    world: Boolean,
    chat: Boolean,
    home: Boolean,
    worldScroll: ScrollState,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val paper = MaterialTheme.colorScheme.background
    val sky = if (dark) Color(0xFF293453) else Color(0xFFE8F5FF)
    val lilac = if (dark) Color(0xFF29263C) else Color(0xFFF0EAFB)
    val floor = if (dark) Color(0xFF242740) else Color(0xFFECE8FF)
    Box(Modifier.fillMaxSize().background(if (world) floor else paper)) {
        if (world) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationY = -worldScroll.value.toFloat()
                }
            ) {
                Image(
                    painterResource(
                        if (dark) R.drawable.mascot_garden_night else R.drawable.mascot_garden
                    ),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, floor)))
                )
            }
        } else if (chat) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        if (home) listOf(sky, lilac, paper) else listOf(sky, paper),
                        endY = if (home) {
                            600.dp.value *
                                androidx.compose.ui.platform.LocalDensity.current.density
                        } else {
                            330.dp.value *
                                androidx.compose.ui.platform.LocalDensity.current.density
                        }
                    )
                )
            )
        }
        content()
    }
}

@Composable
internal fun worldSkyColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < .5f) {
    Color(0xFF293453)
} else {
    Color(0xFFC9EDFF)
}

internal enum class MascotMotion { ARRIVE, SEARCH, CELEBRATE }

@Composable
internal fun Mascot(
    @DrawableRes pose: Int,
    modifier: Modifier = Modifier,
    motion: MascotMotion = MascotMotion.ARRIVE,
    focused: Boolean = false
) {
    val less = LocalLessMotion.current
    val progress = remember(motion) { Animatable(0f) }
    LaunchedEffect(motion, less) {
        if (less) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(
                    when (motion) {
                        MascotMotion.ARRIVE -> 700
                        MascotMotion.SEARCH -> 2400
                        MascotMotion.CELEBRATE -> 850
                    },
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    val lean by animateFloatAsState(
        if (focused && !less) 7f else 0f,
        tween(if (less) 0 else 250),
        label = "curious-lean"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Image(
        painterResource(pose),
        null,
        modifier.graphicsLayer {
            val p = progress.value
            val remaining = if (less) 0f else 1f - p
            when (motion) {
                MascotMotion.ARRIVE -> {
                    translationY = 10f * density * remaining
                    rotationZ = -5f * remaining + lean
                }

                MascotMotion.SEARCH -> {
                    rotationZ = if (less) {
                        0f
                    } else {
                        (-2f + 2f * kotlin.math.cos(p * 6f * Math.PI).toFloat())
                    }
                }

                MascotMotion.CELEBRATE -> {
                    translationY = if (less) {
                        0f
                    } else {
                        (-14f * kotlin.math.sin(p * Math.PI).toFloat() + 12f * remaining) * density
                    }
                    rotationZ = -6f * kotlin.math.sin(p * Math.PI).toFloat() * remaining
                }
            }
        },
        contentScale = ContentScale.Fit
    )
}

@Composable
internal fun WorldScene(
    modifier: Modifier = Modifier,
    @DrawableRes pose: Int = R.drawable.mascot_reading,
    celebrate: Boolean = false,
    fullWorld: Boolean = false,
    size: Dp = 240.dp
) {
    val lessMotion = LocalLessMotion.current
    val ground = remember { Animatable(1f) }
    LaunchedEffect(celebrate, lessMotion) {
        if (celebrate && !lessMotion) {
            ground.animateTo(
                1f,
                keyframes {
                    durationMillis = 850
                    .7f at 0
                    .55f at 340
                    1.13f at 638
                    1f at 850
                }
            )
        } else {
            ground.snapTo(1f)
        }
    }
    val shadow = if (MaterialTheme.colorScheme.background.luminance() < .5f) {
        Color(0x550A112A)
    } else {
        Color(0x3355509E)
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (!fullWorld) {
            val dark = MaterialTheme.colorScheme.background.luminance() < .5f
            Image(
                painterResource(
                    if (dark) R.drawable.mascot_garden_night else R.drawable.mascot_garden
                ),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, .36f)
            )
        }
        Canvas(
            Modifier.align(Alignment.BottomCenter).offset(y = -size * .10f).size(size * .6f, 16.dp)
                .graphicsLayer {
                    scaleX = ground.value
                    alpha = ground.value.coerceAtMost(1f)
                }
        ) {
            drawOval(
                shadow,
                topLeft = Offset.Zero,
                size = Size(this.size.width, this.size.height)
            )
        }
        Mascot(
            pose,
            Modifier.size(size),
            if (celebrate) MascotMotion.CELEBRATE else MascotMotion.ARRIVE
        )
    }
}

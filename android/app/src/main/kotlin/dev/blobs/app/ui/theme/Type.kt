package dev.blobs.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.blobs.app.R

@OptIn(ExperimentalTextApi::class)
private val Nunito = FontFamily(
    Font(
        R.font.nunito,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)
private val DmSans =
    FontFamily(
        Font(R.font.dm_sans_regular, FontWeight.Normal),
        Font(R.font.dm_sans_medium, FontWeight.Medium),
        Font(R.font.dm_sans_semibold, FontWeight.SemiBold)
    )

internal val BlobsTypography =
    Typography(
        displayLarge = TextStyle(
            fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 44.sp,
            lineHeight = 50.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            lineHeight = 38.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 34.sp
        ),
        titleLarge = TextStyle(
            fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp
        ),
        titleMedium = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        bodySmall = TextStyle(
            fontFamily = DmSans,
            fontSize = 13.sp,
            lineHeight = 19.sp
        ),
        labelMedium = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelSmall = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = DmSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
    )

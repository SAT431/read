package dev.folio.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val FolioTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-.5).sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    labelSmall = TextStyle(fontSize = 12.sp, letterSpacing = .6.sp),
)

package io.sentry.ui

import androidx.compose.ui.graphics.Color

object Colors {

    val STATUS_OK = Color(43, 161, 133)
    val STATUS_NEUTRAL = Color(90, 90, 90)
    val PERF = Color(235, 192, 0)
    val STATUS_ERROR = Color(245, 84, 89)
    val STATUS_PENDING = PERF

    val themeColors = listOf(
        Color(108, 95, 199),
        Color(60, 116, 221),
        Color(43, 161, 133),
        Color(235, 192, 0),
        Color(245, 84, 89),
        Color(249, 26, 138),
    )

    val themeColorsMuted = listOf(
        Color(0xFF6559C5),
        Color(0xFF2562D4),
        Color(0xFF207964),
        Color(0xFF856C00),
        Color(0xFFCF2126),
        Color(0xFFD1056B),
    )

    fun getColor(index: Int) = themeColors[index % themeColors.size]
    fun getColorMuted(index: Int) = themeColorsMuted[index % themeColors.size]

}
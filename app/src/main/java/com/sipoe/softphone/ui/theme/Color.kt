package com.sipoe.softphone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val AcceptGreenLight = Color(0xFF2E7D32)
private val AcceptGreenDark = Color(0xFF81C784)
private val HangupRedLight = Color(0xFFD32F2F)
private val HangupRedDark = Color(0xFFEF5350)
private val WarningAmberLight = Color(0xFFB26A00)
private val WarningAmberDark = Color(0xFFFFB74D)

@Immutable
data class SipoeColors(
    val accept: Color,
    val onAccept: Color,
    val hangup: Color,
    val onHangup: Color,
    val warning: Color,
)

internal val LightSipoeColors = SipoeColors(
    accept = AcceptGreenLight,
    onAccept = Color.White,
    hangup = HangupRedLight,
    onHangup = Color.White,
    warning = WarningAmberLight,
)

internal val DarkSipoeColors = SipoeColors(
    accept = AcceptGreenDark,
    onAccept = Color(0xFF00390A),
    hangup = HangupRedDark,
    onHangup = Color(0xFF690005),
    warning = WarningAmberDark,
)

val LocalSipoeColors = staticCompositionLocalOf { LightSipoeColors }

internal val LightBrandColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7EC),
    onSecondaryContainer = Color(0xFF051F23),
)

internal val DarkBrandColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF4FD8EB),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCDE7EC),
)

package com.sipoe.softphone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

object SipoeTheme {
    val colors: SipoeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSipoeColors.current
}

@Composable
fun SipoeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkBrandColors else LightBrandColors
    val sipoeColors = if (darkTheme) DarkSipoeColors else LightSipoeColors
    CompositionLocalProvider(LocalSipoeColors provides sipoeColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

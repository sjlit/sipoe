package com.sipoe.softphone.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipoe.softphone.ui.theme.SipoeTheme

data class DialKey(val label: String, val tone: Int, val letters: String = "")

val DIAL_KEYS: List<List<DialKey>> = listOf(
    listOf(
        DialKey("1", ToneGenerator.TONE_DTMF_1),
        DialKey("2", ToneGenerator.TONE_DTMF_2, "ABC"),
        DialKey("3", ToneGenerator.TONE_DTMF_3, "DEF"),
    ),
    listOf(
        DialKey("4", ToneGenerator.TONE_DTMF_4, "GHI"),
        DialKey("5", ToneGenerator.TONE_DTMF_5, "JKL"),
        DialKey("6", ToneGenerator.TONE_DTMF_6, "MNO"),
    ),
    listOf(
        DialKey("7", ToneGenerator.TONE_DTMF_7, "PQRS"),
        DialKey("8", ToneGenerator.TONE_DTMF_8, "TUV"),
        DialKey("9", ToneGenerator.TONE_DTMF_9, "WXYZ"),
    ),
    listOf(
        DialKey("*", ToneGenerator.TONE_DTMF_S),
        DialKey("0", ToneGenerator.TONE_DTMF_0, "+"),
        DialKey("#", ToneGenerator.TONE_DTMF_P),
    ),
)

@Composable
fun Dialpad(
    onKeyPress: (DialKey) -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 84.dp,
    onLongZero: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    fun press(key: DialKey) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        toneGenerator?.startTone(key.tone, 90)
        onKeyPress(key)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DIAL_KEYS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                row.forEach { key ->
                    DialKeyButton(
                        key = key,
                        size = keySize,
                        onPress = { press(key) },
                        onLongPress = if (key.label == "0") onLongZero else null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKeyButton(
    key: DialKey,
    size: Dp,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val sipoe = SipoeTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(sipoe.dialKey)
            .combinedClickable(onClick = onPress, onLongClick = onLongPress)
            .semantics { contentDescription = key.label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.label,
                fontSize = if (key.letters.isEmpty()) 32.sp else 28.sp,
                fontWeight = FontWeight.Light,
                color = sipoe.onDialKey,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = sipoe.onDialKey.copy(alpha = 0.75f),
                )
            }
        }
    }
}

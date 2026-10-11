package org.proxydroid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Visible inside bounds; reveal the whole control, never just its thumb. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tvRemoteFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val reveal = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val color = if (MaterialTheme.colorScheme.surface.luminance() < 0.4f)
        Color(0xFFFFD166) else Color(0xFF075AA8)
    this.bringIntoViewRequester(reveal).onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) scope.launch {
            withFrameNanos { }
            reveal.bringIntoView()
        }
    }.drawWithContent {
        drawContent()
        if (focused) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            drawRoundRect(color, Offset(inset, inset),
                Size((size.width-stroke).coerceAtLeast(0f), (size.height-stroke).coerceAtLeast(0f)),
                CornerRadius(12.dp.toPx()), style = Stroke(stroke))
        }
    }
}

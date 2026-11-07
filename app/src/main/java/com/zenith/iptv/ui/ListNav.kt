package com.zenith.iptv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Remote-control navigation for long vertical lists (channels):
 *  - Up on the first row goes to the last one, Down on the last goes back to the first;
 *  - CH+/CH−, Page up/down, ⏩/⏪ (and optionally ←/→) jump a whole page.
 * [offset] = number of LazyColumn items placed before row 0 (headers).
 */
class ListNav internal constructor(private val state: LazyListState, private val scope: CoroutineScope, private val offset: Int) {
    private var target by mutableIntStateOf(-1)
    private val fr = FocusRequester()

    fun jumpTo(i: Int) {
        target = i
        scope.launch {
            val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            state.scrollToItem((i + offset - visible / 2).coerceAtLeast(0))
            repeat(6) {
                withFrameNanos { }
                if (runCatching { fr.requestFocus() }.isSuccess) return@launch
            }
        }
    }

    private fun pageSize(): Int = (state.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)

    /** Modifier for row [i] of [count] rows. */
    fun row(i: Int, count: Int, leftRightPages: Boolean = false): Modifier {
        val base = if (i == target) Modifier.focusRequester(fr) else Modifier
        return base.onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown || count < 2) return@onPreviewKeyEvent false
            val last = count - 1
            fun go(j: Int): Boolean { jumpTo(j.coerceIn(0, last)); return true }
            when (ev.key) {
                Key.DirectionUp -> if (i == 0) go(last) else false
                Key.DirectionDown -> if (i == last) go(0) else false
                Key.PageDown, Key.ChannelUp, Key.MediaFastForward -> go(if (i == last) 0 else i + pageSize())
                Key.PageUp, Key.ChannelDown, Key.MediaRewind -> go(if (i == 0) last else i - pageSize())
                Key.DirectionRight -> if (leftRightPages) go(if (i == last) 0 else i + pageSize()) else false
                Key.DirectionLeft -> if (leftRightPages) go(if (i == 0) last else i - pageSize()) else false
                else -> false
            }
        }
    }
}

@Composable
fun rememberListNav(state: LazyListState, offset: Int = 0): ListNav {
    val scope = rememberCoroutineScope()
    return remember(state, offset) { ListNav(state, scope, offset) }
}

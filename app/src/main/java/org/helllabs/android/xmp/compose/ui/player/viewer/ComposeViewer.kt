package org.helllabs.android.xmp.compose.ui.player.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

// TODO: This is a WIP

@Suppress("ArrayInDataClass")
data class SampleData(
    var key: Int = 0,
    var ins: Int = 0,
    var holdKey: IntArray = intArrayOf(),
    var period: Int = 0,
    var scopeWidth: Int = 0,
    var buffer: Array<ByteArray> = arrayOf()
)

@Composable
internal fun XmpCanvas(
    modifier: Modifier = Modifier,
    onChangeViewer: () -> Unit,
    currentViewer: Int,
    viewInfo: ViewerInfo,
    patternInfo: PatternInfo = PatternInfo(),
    isMuted: BooleanArray,
    modVars: IntArray,
    insName: Array<String>
) {
    Box(modifier = modifier) {
        when (currentViewer) {
            0 -> InstrumentViewer(onChangeViewer, viewInfo, isMuted, modVars, insName)
            1 -> ComposePatternViewer(onChangeViewer, viewInfo, patternInfo, isMuted, modVars)
            2 -> ComposeChannelViewer(onChangeViewer, viewInfo, isMuted, modVars, insName)
        }
    }
}

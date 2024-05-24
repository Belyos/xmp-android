package org.helllabs.android.xmp.compose.ui.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import com.theapache64.rebugger.Rebugger
import org.helllabs.android.xmp.compose.theme.XmpTheme
import org.helllabs.android.xmp.compose.ui.player.PlayerViewModel

/**
 * Height-less [androidx.compose.material3.BottomAppBar]
 */
@Composable
fun PlayerBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shape = RectangleShape,
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,

            content = content
        )
    }

    Rebugger(
        composableName = "PlayerBottom",
        trackMap = mapOf(
            "modifier" to modifier,
            "containerColor" to containerColor,
            "contentColor" to contentColor,
            "tonalElevation" to tonalElevation,
            "contentPadding" to contentPadding,
            "windowInsets" to windowInsets,
            "content" to content,
        ),
    )
}

@Preview
@Composable
private fun Preview_PlayerBottomAppBar() {
    XmpTheme(useDarkTheme = true) {
        PlayerBottomAppBar {
            PlayerInfo(
                state = PlayerViewModel.PlayerInfoState(
                    infoSpeed = "11",
                    infoBpm = "22",
                    infoPos = "33",
                    infoPat = "44"
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlayerSeekBar(
                state = PlayerViewModel.PlayerTimeState(
                    timeNow = "00:00",
                    timeTotal = "00:00",
                    seekPos = 25f,
                    seekMax = 100f
                ),
                onIsSeeking = { },
                onSeek = { }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlayerControls(
                state = PlayerViewModel.PlayerButtonsState(
                    isPlaying = false,
                    isRepeating = false
                ),
                onEvent = { },
            )
        }
    }
}

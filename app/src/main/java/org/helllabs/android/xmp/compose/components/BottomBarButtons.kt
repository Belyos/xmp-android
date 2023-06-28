package org.helllabs.android.xmp.compose.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BottomBarButtons(
    isShuffle: Boolean,
    isLoop: Boolean,
    onShuffle: (value: Boolean) -> Unit,
    onLoop: (value: Boolean) -> Unit,
    onPlayAll: () -> Unit
) {
    BottomAppBar(
        actions = {
            IconToggleButton(
                checked = isShuffle,
                onCheckedChange = { onShuffle(it) },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContentColor = Color.Green
                ),
                content = {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                }
            )
            IconToggleButton(
                checked = isLoop,
                onCheckedChange = { onLoop(it) },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContentColor = Color.Green
                ),
                content = {
                    Icon(Icons.Filled.Repeat, contentDescription = null)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPlayAll,
                containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                content = {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                }
            )
        }
    )
}

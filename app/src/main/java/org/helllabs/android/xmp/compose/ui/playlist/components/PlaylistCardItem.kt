package org.helllabs.android.xmp.compose.ui.playlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import me.saket.cascade.CascadeDropdownMenu
import org.helllabs.android.xmp.PrefManager
import org.helllabs.android.xmp.compose.components.XmpDropdownMenuHeader
import org.helllabs.android.xmp.model.DropDownItem
import org.helllabs.android.xmp.model.PlaylistItem

private val playlistItemDropDownItems: List<DropDownItem>
    get() {
        val mode = PrefManager.playlistMode
        return mutableListOf<DropDownItem>().apply {
            add(DropDownItem("Remove from playlist", 0))
            add(DropDownItem("Add to play queue", 1))
            add(DropDownItem("Add all to play queue", 2))
            if (mode != 2) add(DropDownItem("Play this module", 3))
            if (mode != 1) add(DropDownItem("Play all starting here", 4))
        }
    }

@Composable
fun PlaylistCardItem(
    elevationColor: Color,
    elevation: Dp,
    item: PlaylistItem,
    onItemClick: () -> Unit,
    onMenuClick: (Int) -> Unit
) {
    var isContextMenuVisible by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = elevationColor
        ),
        elevation = CardDefaults.cardElevation(
            draggedElevation = elevation
        )
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClick() },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(text = item.name)
            },
            supportingContent = {
                Text(text = item.comment)
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isContextMenuVisible = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null
                    )
                }

                CascadeDropdownMenu(
                    expanded = isContextMenuVisible,
                    onDismissRequest = { isContextMenuVisible = false }
                ) {
                    XmpDropdownMenuHeader(
                        text = "Edit Playlist"
                    )

                    playlistItemDropDownItems.forEach {
                        DropdownMenuItem(
                            text = { Text(text = it.text) },
                            onClick = {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                                onMenuClick(it.index)
                                isContextMenuVisible = false
                            }
                        )
                    }
                }
            }
        )
    }
}

package org.helllabs.android.xmp.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.helllabs.android.xmp.R
import org.helllabs.android.xmp.browser.playlist.PlaylistItem
import org.helllabs.android.xmp.compose.theme.XmpTheme

@Composable
fun ErrorMessageDialog(
    isShowing: Boolean,
    icon: ImageVector = Icons.Default.Error,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit
) {
    if (!isShowing) {
        return
    }

    AlertDialog(
        onDismissRequest = onConfirm,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText)
            }
        }
    )
}

@Composable
fun NewPlaylistDialog(
    isShowing: Boolean,
    onConfirm: (newName: String, newComment: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isShowing) {
        return
    }

    var newName by remember { mutableStateOf("") }
    var newComment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.menu_new_playlist))
        },
        text = {
            Column {
                Text(text = stringResource(id = R.string.dialog_new_playlist))
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    label = {
                        Text(text = "Name")
                    },
                    value = newName,
                    onValueChange = { newName = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    label = {
                        Text(text = "Comment")
                    },
                    value = newComment,
                    onValueChange = { newComment = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotEmpty(),
                onClick = { onConfirm(newName, newComment) }
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun EditPlaylistDialog(
    isShowing: Boolean,
    playlistItem: PlaylistItem?,
    onConfirm: (oldName: String, newName: String, oldComment: String, newComment: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (name: String) -> Unit
) {
    if (!isShowing || playlistItem == null) {
        return
    }

    var newName by remember { mutableStateOf(playlistItem.name) }
    var newComment by remember { mutableStateOf(playlistItem.comment) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier
            .padding(28.dp)
            .wrapContentHeight(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
        },
        title = {
            Text(text = "Edit Playlist")
        },
        text = {
            Column {
                Text(text = "Enter a new name or comment for ${playlistItem.name}")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = newName, onValueChange = { newName = it })
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = newComment, onValueChange = { newComment = it })

                if (confirmDelete) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Press delete again to delete playlist")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotEmpty(),
                onClick = {
                    onConfirm(
                        playlistItem.name,
                        newName,
                        playlistItem.comment,
                        newComment
                    )
                }
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!confirmDelete) {
                        confirmDelete = true
                        return@TextButton
                    }

                    onDelete(playlistItem.name)
                }
            ) {
                Text(text = stringResource(id = R.string.menu_delete))
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun ChangeLogDialog(
    isShowing: Boolean,
    onDismiss: () -> Unit
) {
    if (!isShowing) {
        return
    }

    AlertDialog(
        modifier = Modifier.heightIn(max = 512.dp),
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Info, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.changelog))
        },
        text = {
            val scrollState = rememberScrollState()

            Column {
                Text(text = stringResource(id = R.string.changelog_title))
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.verticalScroll(scrollState)
                ) {
                    Text(text = stringResource(id = R.string.changelog_text))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dismiss))
            }
        }
    )
}

@Composable
fun TextInputDialog(
    isShowing: Boolean,
    icon: ImageVector? = null,
    title: String,
    text: String? = null,
    defaultText: String,
    onConfirm: (value: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isShowing) {
        return
    }

    var value by remember { mutableStateOf(defaultText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            icon?.let {
                Icon(imageVector = it, contentDescription = null)
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            Column {
                text?.let {
                    Text(text = it)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = { onConfirm(value) }
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun Preview_NewPlaylistDialog() {
    XmpTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            NewPlaylistDialog(
                isShowing = true,
                onConfirm = { _, _ -> },
                onDismiss = { }
            )
        }
    }
}

@Preview
@Composable
fun Preview_MessageDialog() {
    XmpTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            ErrorMessageDialog(
                isShowing = true,
                title = stringResource(id = R.string.error),
                text = stringResource(id = R.string.error_create_playlist),
                confirmText = stringResource(id = R.string.ok),
                onConfirm = { }
            )
        }
    }
}

@Preview
@Composable
fun Preview_EditPlaylistDialog() {
    XmpTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            EditPlaylistDialog(
                isShowing = true,
                playlistItem = PlaylistItem(
                    PlaylistItem.TYPE_PLAYLIST,
                    "Playlist Name",
                    "Playlist Comment"
                ),
                onConfirm = { _, _, _, _ -> },
                onDismiss = {},
                onDelete = {}
            )
        }
    }
}

@Preview
@Composable
fun Preview_ChangeLogDialog() {
    XmpTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            ChangeLogDialog(
                isShowing = true,
                onDismiss = {}
            )
        }
    }
}

@Preview
@Composable
fun Preview_TextInputDialog() {
    XmpTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextInputDialog(
                isShowing = true,
                icon = Icons.Default.Info,
                title = "Some Title",
                text = "Some Text",
                defaultText = "Default Text",
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}

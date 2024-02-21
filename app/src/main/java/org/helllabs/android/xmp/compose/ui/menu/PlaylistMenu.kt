package org.helllabs.android.xmp.compose.ui.menu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.helllabs.android.xmp.BuildConfig
import org.helllabs.android.xmp.R
import org.helllabs.android.xmp.compose.components.EditPlaylistDialog
import org.helllabs.android.xmp.compose.components.ErrorScreen
import org.helllabs.android.xmp.compose.components.MessageDialog
import org.helllabs.android.xmp.compose.components.NewPlaylistDialog
import org.helllabs.android.xmp.compose.components.ProgressbarIndicator
import org.helllabs.android.xmp.compose.theme.XmpTheme
import org.helllabs.android.xmp.compose.theme.michromaFontFamily
import org.helllabs.android.xmp.compose.theme.themedText
import org.helllabs.android.xmp.compose.ui.filelist.FileListActivity
import org.helllabs.android.xmp.compose.ui.menu.components.MenuCardItem
import org.helllabs.android.xmp.compose.ui.player.PlayerActivity
import org.helllabs.android.xmp.compose.ui.playlist.PlaylistActivity
import org.helllabs.android.xmp.compose.ui.preferences.Preferences
import org.helllabs.android.xmp.compose.ui.search.Search
import org.helllabs.android.xmp.core.PlaylistManager
import org.helllabs.android.xmp.core.PrefManager
import org.helllabs.android.xmp.core.StorageManager
import org.helllabs.android.xmp.model.FileItem
import org.helllabs.android.xmp.service.PlayerService
import timber.log.Timber

class PlaylistMenu : ComponentActivity() {

    private val viewModel by viewModels<PlaylistMenuViewModel>()

    private var snackBarHostState = SnackbarHostState()

    private val settingsContract = ActivityResultContracts.StartActivityForResult()
    private val settingsResult = registerForActivityResult(settingsContract) {
        viewModel.updateList()
    }

    private val playlistContract = ActivityResultContracts.StartActivityForResult()
    private val playlistResult = registerForActivityResult(playlistContract) {
        viewModel.updateList()
    }

    private val playerContract = ActivityResultContracts.StartActivityForResult()
    private var playerResult = registerForActivityResult(playerContract) { result ->
        if (result.resultCode == 1) {
            result.data?.getStringExtra("error")?.let {
                Timber.w("Result with error: $it")
                lifecycleScope.launch {
                    snackBarHostState.showSnackbar(message = it)
                }
            }
        }
        if (result.resultCode == 2) {
            viewModel.updateList()
        }
    }

    private val documentTreeContract = ActivityResultContracts.OpenDocumentTree()
    private val documentTreeResult = registerForActivityResult(documentTreeContract) { uri ->
        StorageManager.setPlaylistDirectory(
            uri = uri,
            onSuccess = {
                // Refresh the list
                viewModel.setDefaultPath()
            },
            onError = {
                viewModel.showError(message = it, isFatal = true)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            )
        )

        if (PlayerService.isAlive && PrefManager.startOnPlayer) {
            if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) {
                Intent(this, PlayerActivity::class.java).also(::startActivity)
            }
        }

        Timber.d("onCreate")
        setContent {
            val context = LocalContext.current
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // User theme setting? Check this out
            // https://github.com/android/nowinandroid/commit/a3ee09ec3e53412e65c1f01d2e8588fecd2b7157

            /**
             * Error message Dialog
             */
            MessageDialog(
                isShowing = state.errorText.isNotEmpty(),
                title = stringResource(id = R.string.error),
                text = state.errorText,
                confirmText = if (state.isFatalError) {
                    stringResource(id = R.string.exit)
                } else {
                    stringResource(id = R.string.ok)
                },
                onConfirm = {
                    if (state.isFatalError) {
                        finish()
                    }

                    viewModel.showError("", false)
                }
            )

            /**
             * Edit playlist dialog
             */
            var changePlaylist by remember { mutableStateOf(false) }
            var changePlaylistInfo: FileItem? by remember { mutableStateOf(null) }
            EditPlaylistDialog(
                isShowing = changePlaylist,
                fileItem = changePlaylistInfo,
                onConfirm = { item, newName, newComment ->
                    val res = PlaylistManager().run {
                        load(item.docFile!!.uri)
                        rename(newName, newComment)
                    }

                    if (!res) {
                        lifecycleScope.launch {
                            val msg = "Failed to edit playlist"
                            snackBarHostState.showSnackbar(msg, "OK")
                        }
                    }

                    changePlaylist = false
                    changePlaylistInfo = null
                    viewModel.updateList()
                },
                onDismiss = {
                    changePlaylist = false
                    changePlaylistInfo = null
                    viewModel.updateList()
                },
                onDelete = { item ->
                    PlaylistManager.delete(item.name)
                    changePlaylist = false
                    changePlaylistInfo = null
                    viewModel.updateList()
                }
            )

            /**
             * New playlist dialog
             */
            var newPlaylist by remember { mutableStateOf(false) }
            NewPlaylistDialog(
                isShowing = newPlaylist,
                onConfirm = { name, comment ->
                    val res = PlaylistManager().run {
                        new(name, comment)
                    }

                    if (res) {
                        viewModel.updateList()
                    } else {
                        viewModel.showError(
                            message = getString(R.string.error_create_playlist),
                            isFatal = false
                        )
                    }

                    newPlaylist = false
                },
                onDismiss = {
                    newPlaylist = false
                }
            )

            // Ask for Permissions
            LaunchedEffect(Unit) {
                val savedUri = PrefManager.safStoragePath.let {
                    try {
                        Uri.parse(it)
                    } catch (e: NullPointerException) {
                        null
                    }
                }
                val persistedUris = contentResolver.persistedUriPermissions
                val hasAccess = persistedUris.any { it.uri == savedUri && it.isWritePermission }

                if (savedUri == null || !hasAccess) {
                    documentTreeResult.launch(null)
                } else {
                    viewModel.setDefaultPath()
                }
            }

            LaunchedEffect(state.mediaPath) {
                if (state.mediaPath.isNotEmpty()) {
                    viewModel.setupDataDir(
                        name = getString(R.string.empty_playlist),
                        comment = getString(R.string.empty_comment),
                        onSuccess = {
                            viewModel.updateList()
                        },
                        onError = {
                            viewModel.showError(it, true)
                        }
                    )
                }
            }

            XmpTheme {
                PlaylistMenuScreen(
                    state = state,
                    snackBarHostState = snackBarHostState,
                    permissionState = StorageManager.checkPermissions(),
                    onItemClick = { item ->
                        if (item.isSpecial) {
                            playlistResult.launch(
                                Intent(context, FileListActivity::class.java)
                            )
                        } else {
                            playlistResult.launch(
                                Intent(context, PlaylistActivity::class.java).apply {
                                    putExtra("name", item.docFile!!.uri.toString())
                                }
                            )
                        }
                    },
                    onItemLongClick = { item ->
                        if (item.isSpecial) {
                            documentTreeResult.launch(null)
                        } else {
                            changePlaylistInfo = item
                            changePlaylist = true
                        }
                    },
                    onRefresh = {
                        viewModel.updateList()
                    },
                    onNewPlaylist = {
                        newPlaylist = true
                    },
                    onTitleClicked = {
                        if (PrefManager.startOnPlayer && PlayerService.isAlive) {
                            Intent(this, PlayerActivity::class.java).also {
                                playerResult.launch(it)
                            }
                        }
                    },
                    onDownload = {
                        Intent(this, Search::class.java).also(::startActivity)
                    },
                    onSettings = {
                        settingsResult.launch(Intent(this, Preferences::class.java))
                    },
                    onRequestSettings = {
                        Intent().apply {
                            action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        }.also(playlistResult::launch)
                    },
                    onRequestStorage = {
                        documentTreeResult.launch(null)
                    }
                )
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (!PrefManager.safStoragePath.isNullOrEmpty()) {
            viewModel.updateList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenuScreen(
    state: PlaylistMenuViewModel.PlaylistMenuState,
    snackBarHostState: SnackbarHostState,
    permissionState: Boolean,
    onItemClick: (item: FileItem) -> Unit,
    onItemLongClick: (item: FileItem) -> Unit,
    onRefresh: () -> Unit,
    onNewPlaylist: () -> Unit,
    onTitleClicked: () -> Unit,
    onDownload: () -> Unit,
    onSettings: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestSettings: () -> Unit
) {
    val scrollState = rememberLazyListState()
    val isScrolled = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        topBar = {
            val topBarContainerColor = if (isScrolled.value) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
            } else {
                MaterialTheme.colorScheme.surface
            }

            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainerColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        enabled = permissionState,
                        onClick = onDownload,
                        content = {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        }
                    )
                    IconButton(
                        enabled = permissionState,
                        onClick = onSettings,
                        content = {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                        }
                    )
                },
                title = {
                    TextButton(
                        enabled = permissionState,
                        onClick = onTitleClicked
                    ) {
                        ProvideTextStyle(
                            LocalTextStyle.current.merge(
                                TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        ) {
                            Text(
                                text = themedText(text = stringResource(id = R.string.app_name)),
                                fontFamily = michromaFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (permissionState) {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(id = R.string.menu_new_playlist)) },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                    expanded = !isScrolled.value,
                    onClick = onNewPlaylist
                )
            }
        }
    ) { paddingValues ->

        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                onRefresh()
                pullRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection),
            contentAlignment = Alignment.Center
        ) {
            if (state.playlistItems.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.playlistItems) { item ->
                        MenuCardItem(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    }
                }
            }

            if (!state.isLoading && !permissionState) {
                ErrorScreen(text = "Unable to access storage for Xmp to use") {
                    Button(
                        onClick = onRequestStorage,
                        content = { Text(text = "Set Directory") }
                    )
                    OutlinedButton(
                        onClick = onRequestSettings,
                        content = { Text(text = "Goto Settings") }
                    )
                }
            }

            PullToRefreshContainer(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullRefreshState
            )

            ProgressbarIndicator(isLoading = state.isLoading)
        }
    }
}

@Preview
@Composable
private fun Preview_PlaylistMenuScreen() {
    XmpTheme(useDarkTheme = true) {
        PlaylistMenuScreen(
            state = PlaylistMenuViewModel.PlaylistMenuState(
                mediaPath = "sdcard\\some\\path",
                isLoading = true,
                playlistItems = List(15) {
                    FileItem(
                        isSpecial = it >= 1,
                        name = "Name $it",
                        comment = "Comment $it",
                        docFile = null
                    )
                }
            ),
            snackBarHostState = SnackbarHostState(),
            permissionState = false,
            onItemClick = {},
            onItemLongClick = {},
            onRefresh = {},
            onNewPlaylist = {},
            onTitleClicked = {},
            onDownload = {},
            onSettings = {},
            onRequestStorage = {},
            onRequestSettings = {}
        )
    }
}

package org.helllabs.android.xmp.preferences

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import org.helllabs.android.xmp.PrefManager
import org.helllabs.android.xmp.R
import org.helllabs.android.xmp.compose.components.XmpTopBar
import org.helllabs.android.xmp.compose.theme.XmpTheme
import org.helllabs.android.xmp.preferences.about.About
import org.helllabs.android.xmp.preferences.about.ListFormats
import timber.log.Timber
import java.io.File

class Preferences : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            XmpTheme {
                PreferencesScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onFormats = { startActivity(Intent(this, ListFormats::class.java)) },
                    onAbout = { startActivity(Intent(this, About::class.java)) },
                    onClearCache = {
                        val result = if (deleteCache(PrefManager.CACHE_DIR)) {
                            getString(R.string.cache_clear)
                        } else {
                            getString(R.string.cache_clear_error)
                        }
                        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    companion object {
        fun deleteCache(file: File): Boolean = deleteCache(file, true)

        @Suppress("SameParameterValue")
        private fun deleteCache(file: File, boolFlag: Boolean): Boolean {
            var flag = boolFlag
            if (!file.exists()) {
                return true
            }
            if (file.isDirectory) {
                for (cacheFile in file.listFiles().orEmpty()) {
                    flag = flag and deleteCache(cacheFile)
                }
            }
            flag = flag and file.delete()
            return flag
        }
    }
}

@Composable
private fun PreferencesScreen(
    onBack: () -> Unit,
    onFormats: () -> Unit,
    onAbout: () -> Unit,
    onClearCache: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isScrolled = remember {
        derivedStateOf {
            scrollState.value > 0
        }
    }

    Scaffold(
        topBar = {
            XmpTopBar(
                title = stringResource(id = R.string.settings),
                isScrolled = isScrolled.value,
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            SettingsGroupPlaylist(onClearCache = onClearCache)
            SettingsGroupSound()
            SettingsGroupInterface()
            SettingsGroupDownload()
            SettingsGroupInformation(onFormats = onFormats, onAbout = onAbout)
        }
    }
}

@Preview
@Composable
private fun Preview_PreferencesScreen() {
    val context = LocalContext.current
    PrefManager.init(context, File("sdcard"))
    XmpTheme {
        PreferencesScreen(
            onBack = {},
            onFormats = {},
            onAbout = {},
            onClearCache = {}
        )
    }
}

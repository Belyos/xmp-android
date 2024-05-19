package org.helllabs.android.xmp.compose.ui.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.helllabs.android.xmp.R
import org.helllabs.android.xmp.compose.components.XmpTopBar
import org.helllabs.android.xmp.compose.theme.XmpTheme
import org.helllabs.android.xmp.compose.theme.topazFontFamily

@Serializable
data class NavSearchError(val error: String? = null)

@Composable
fun ErrorScreen(
    message: String?,
    onBack: () -> Unit
) {
    val errorMsg = remember {
        message?.substringAfter("Exception: ")?.trim()
    }

    Scaffold(
        topBar = {
            XmpTopBar(
                onBack = onBack,
                title = stringResource(id = R.string.search_title_error)
            )
        }
    ) { paddingValues ->
        GuruFrame(
            modifier = Modifier.padding(paddingValues),
            message = when {
                errorMsg.isNullOrEmpty() -> stringResource(R.string.search_unknown_error)
                else -> stringResource(
                    R.string.search_known_error,
                    errorMsg.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                )
            }
        )
    }
}

@Composable
private fun GuruFrame(
    modifier: Modifier,
    message: String
) {
    val scope = rememberCoroutineScope()
    var frameState by remember { mutableStateOf(true) }

    LaunchedEffect(frameState) {
        // Guru Meditation Frame
        scope.launch {
            delay(1337L)
            frameState = !frameState
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(5.dp, if (frameState) Color.Red else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            fontFamily = topazFontFamily,
            fontSize = 16.sp,
            color = Color.Red
        )
    }
}

@Preview
@Composable
private fun Preview_ErrorScreen() {
    XmpTheme(useDarkTheme = true) {
        ErrorScreen(
            message = null,
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun Preview_ErrorScreen_WithMessage() {
    XmpTheme(useDarkTheme = true) {
        ErrorScreen(
            message = "Exception: Some Error Message",
            onBack = {}
        )
    }
}

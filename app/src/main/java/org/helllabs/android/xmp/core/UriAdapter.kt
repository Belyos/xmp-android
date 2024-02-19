package org.helllabs.android.xmp.core

import android.net.Uri
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Moshi adapter to handle URI parsing
 */
@Suppress("unused")
class UriAdapter {
    @ToJson
    fun toJson(uri: Uri): String = uri.toString()

    @FromJson
    fun fromJson(uriString: String): Uri = Uri.parse(uriString)
}

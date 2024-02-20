package org.helllabs.android.xmp.core

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.dfc.file.DocumentFileCompat
import org.helllabs.android.xmp.XmpApplication
import org.helllabs.android.xmp.core.Constants.DEFAULT_DOWNLOAD_DIR
import org.helllabs.android.xmp.model.Module
import timber.log.Timber


/**
 * This object class is kinda of a mash up of anything related to SAF and the Document Tree
 */
object StorageManager {

    /**
     * Checks if we have a URI in preferences, then checks to see if we have R/W access
     */
    fun checkPermissions(): Boolean {
        val context = XmpApplication.instance!!.applicationContext

        val isPreferenceEmpty = PrefManager.safStoragePath.isNullOrBlank()
        if (isPreferenceEmpty) {
            return false
        }

        val preference = Uri.parse(PrefManager.safStoragePath)
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        return persistedUriPermissions.any {
            it.uri == preference && it.isReadPermission && it.isWritePermission
        }
    }

    /**
     * Get our parent/root directory
     */
    fun getParentDirectory(onError: (String) -> Unit): DocumentFileCompat? {
        val context = XmpApplication.instance!!.applicationContext

        val prefUri = PrefManager.safStoragePath.let { Uri.parse(it) }

        if (prefUri == null) {
            onError("Getting saved uri returned null")
            return null
        }

        val parent = DocumentFileCompat.fromTreeUri(context, prefUri)
        if (parent == null) {
            onError("Getting parent directory returned null")
            return null
        }

        return parent
    }

    /**
     * Get the playlist directory that was set
     */
    fun getPlaylistDirectory(onError: (String) -> Unit = {}): DocumentFileCompat? {
        return getParentDirectory(
            onError = {
                Timber.e(it)
                onError(it)
            }
        )?.findFile("playlists")
    }

    /**
     * Get the mod directory
     * This will be where modules are downloaded,
     * and where File Explorer should start
     */
    fun getModDirectory(onError: (String) -> Unit = {}): DocumentFileCompat? {
        return getParentDirectory(
            onError = {
                Timber.e(it)
                onError(it)
            }
        )?.findFile("mods")
    }

    /**
     * Set the playlist directory to the specified URI
     * Create `playlist` and `mod` folders respectively.
     */
    fun setPlaylistDirectory(
        uri: Uri?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (uri == null) {
            onError("Setting playlist directory uri was null")
            return
        }

        val context = XmpApplication.instance!!.applicationContext

        // Save our Uri
        PrefManager.safStoragePath = uri.toString()

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.contentResolver.takePersistableUriPermission(uri, flags)

        // Get parent directory
        val parentDocument = getParentDirectory(onError) ?: return

        // Create sub directories
        listOf("mods", "playlists").forEach { directoryName ->
            val exists = parentDocument.findFile(directoryName) != null
            if (!exists) {
                parentDocument.createDirectory(directoryName)

                if (directoryName == "mods") {
                    // Install examples if allowed and an empty mod folder
                    val modDir = parentDocument.findFile("mods")
                    installExampleMod(modDir)
                }
            }
        }

        onSuccess()
    }

    /**
     * Get the name of the default path we're allowed to work in.
     */
    fun getDefaultPathName(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val parent = getParentDirectory(
            onError = {
                onError("Default path name uri was null")
            }
        )

        if (parent?.name == null) {
            onError("Getting default root path name is null ")
            return
        }

        onSuccess(parent.name)
    }

    /**
     * Attempt to install sample modules in our assets folder. Skip if it exists
     */
    fun installExampleMod(modPath: DocumentFileCompat?): Boolean {
        if (!PrefManager.examples) {
            return true
        }

        if (modPath == null) {
            Timber.w("modDir is null")
            return false
        }

        val context = XmpApplication.instance!!.applicationContext
        runCatching {
            val assets = context.resources.assets
            assets.list("mod")?.forEach { asset ->
                val mod = modPath.findFile(asset) ?: return@forEach
                if (mod.exists()) {
                    Timber.i("Skipping $asset")
                    return@forEach
                }

                val inStream = assets.open("mod/$asset")
                val file = modPath.createFile("application/octet-stream", asset)
                val outStream = context.contentResolver.openOutputStream(file!!.uri)
                    ?: return@forEach

                inStream.copyTo(outStream)
            }
        }.onFailure { exception ->
            Timber.e(exception)
            return false
        }

        return true
    }

    /**
     * Get the download path a mod should be downloaded to.
     *
     * @see [PrefManager.modArchiveFolder] if the pref was set to download
     * @see [PrefManager.artistFolder]
     */
    fun getDownloadPath(
        module: Module,
        onSuccess: (DocumentFileCompat) -> Unit,
        onError: (String) -> Unit
    ) {
        val rootDir = getModDirectory()
        if (rootDir == null || !rootDir.isDirectory()) {
            onError("Unable to access the mod directory.")
            return
        }

        var targetDir = rootDir

        if (PrefManager.modArchiveFolder) {
            val modArchiveDir = targetDir.findFile(DEFAULT_DOWNLOAD_DIR)
                ?: targetDir.createDirectory(DEFAULT_DOWNLOAD_DIR)

            if (modArchiveDir == null || !modArchiveDir.isDirectory()) {
                onError("Failed to access or create TMA directory.")
                return
            }

            targetDir = modArchiveDir
        }

        if (PrefManager.artistFolder) {
            val artistName = module.getArtist()
            val artistDir = targetDir.findFile(artistName)
                ?: targetDir.createDirectory(artistName)

            if (artistDir == null || !artistDir.isDirectory()) {
                onError("Failed to access or create the artist directory.")
                return
            }

            targetDir = artistDir
        }

        onSuccess(targetDir)
    }

    // TODO, this seems to list the parent dir, then parse through playlists when we actually want 'mod' like we're passing
    fun walkDownTree(uri: Uri?): List<Uri> {
        if (uri == null) {
            return emptyList()
        }

        fun collect(directory: DocumentFileCompat, uris: MutableList<Uri>) {
            Timber.d("Parent: ${directory.parentFile?.name}")
            directory.listFiles().forEach {
                Timber.d("-> ${it.uri}")
            }
//            Timber.d("Processing: ${directory.name}")
//            if (directory.isFile()) {
//                uris.add(directory.uri)
//            } else if (directory.isDirectory()) {
//                val files = directory.listFiles()
//                for (file in files) {
//                    Timber.d("Dir: ${file.uri}")
//                    collect(file, uris)
//                }
//            }
        }

        Timber.d("Walking down $uri")
        val context = XmpApplication.instance!!.applicationContext
        val startTree = DocumentFileCompat.fromTreeUri(context, uri) ?: return emptyList()
        val uris: MutableList<Uri> = mutableListOf()
        collect(startTree, uris)

        return uris
    }

    fun getFilename(uri: Uri?): String {
        if (uri == null) {
            return ""
        }

        val context = XmpApplication.instance!!.applicationContext
        val docFile = DocumentFileCompat.fromSingleUri(context, uri)

        return docFile?.name ?: ""
    }

    fun deleteFileOrDirectory(uri: Uri?): Boolean {
        if (uri == null) {
            return false
        }

        val context = XmpApplication.instance!!.applicationContext
        val docFile = DocumentFileCompat.fromSingleUri(context, uri)

        return docFile?.delete() ?: false
    }

    fun doesModuleExist(module: Module?): Boolean {
        if (module == null || module.url.isBlank()) {
            return false
        }

        var exists = false
        doesModuleExist(
            module = module,
            onFound = { exists = true },
            onNotFound = { exists = false },
            onError = { exists = false }
        )

        return exists
    }

    /**
     * Check if a module exists in a location given the preferences
     * @see [PrefManager.artistFolder]
     * @see [PrefManager.modArchiveFolder]
     */
    fun doesModuleExist(
        module: Module?,
        onFound: (Uri) -> Unit,
        onNotFound: (DocumentFileCompat) -> Unit,
        onError: (String) -> Unit
    ) {
        if (module == null || module.url.isBlank()) {
            onError("Module or module URL is null or blank.")
            return
        }

        getDownloadPath(
            module = module,
            onSuccess = { directory ->
                val moduleFilename = module.url.substringAfterLast('#')
                directory.findFile(moduleFilename)?.let { file ->
                    if (file.exists() && !file.isDirectory()) {
                        onFound(file.uri)
                    } else {
                        onNotFound(directory)
                    }
                } ?: onNotFound(directory)
            },
            onError = onError
        )
    }

    fun deleteModule(module: Module?): Boolean {
        if (module == null || module.url.isBlank()) {
            Timber.w("Module was null")
            return false
        }

        var res = false
        getDownloadPath(
            module = module,
            onSuccess = { directory ->
                val moduleFilename = module.url.substringAfterLast('#')
                directory.findFile(moduleFilename)?.let { file ->
                    res = file.delete()
                }
            },
            onError = { }
        )
        return res
    }
}

package org.helllabs.android.xmp.core

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.DocumentFileCompat
import java.util.Locale
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
     * Set the playlist directory to the specified [Uri]
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

        PrefManager.safStoragePath = uri.toString()

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.contentResolver.takePersistableUriPermission(uri, flags)

        val parentDocument = getParentDirectory(onError) ?: return

        listOf("mods", "playlists").forEach { directoryName ->
            val exists = parentDocument.findFile(directoryName) != null
            if (!exists) {
                parentDocument.createDirectory(directoryName)

                if (directoryName == "mods") {
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

    /**
     * Delete a File or Directory
     *
     * @param docFile the [DocumentFileCompat] to be deleted
     *
     * @return true if successful, otherwise false
     */
    fun deleteFileOrDirectory(docFile: DocumentFileCompat?): Boolean {
        if (docFile == null) {
            return false
        }

        return deleteFileOrDirectory(docFile.uri)
    }

    /**
     * Delete a File or Directory
     *
     * @param uri the [Uri] to be deleted
     *
     * @return true if successful, otherwise false
     */
    fun deleteFileOrDirectory(uri: Uri?): Boolean {
        if (uri == null) {
            return false
        }

        val context = XmpApplication.instance!!.applicationContext
        val docFile = DocumentFileCompat.fromSingleUri(context, uri)

        return docFile?.delete() ?: false
    }

    /**
     *  Check if a module exists in a location given the preferences
     *
     *  @param module the [Module] in question
     *
     *  @return true if it exists, otherwise false
     */
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
     *
     *  @param module the [Module] in question
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

    /**
     * Delete a recently downloaded module
     *
     * @param module the [Module] to be deleted
     *
     * @return if successful or not
     */
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

    /**
     * A Top-Down File Walker
     *
     * Will walk down a given uri and collect uris in alphabetical order, folders first
     *
     * @param uri the URI to begin walking
     * @param includeDirectories whether to add directories in the list to return
     *
     * @return a list of [Uri]'s in order.
     */
    fun walkDownDirectory(uri: Uri?, includeDirectories: Boolean = true): List<Uri> {
        if (uri == null) {
            return emptyList()
        }

        val context = XmpApplication.instance!!.applicationContext
        val docId = DocumentsContract.getDocumentId(uri)
        val childDocUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        val items = mutableListOf<Pair<String, Uri>>()

        // Couldn't figure out sortOrder
        context.contentResolver.query(childDocUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val childDocumentId = cursor.getString(idCol)
                val mimeType = cursor.getString(mimeCol)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childDocumentId)

                items.add(Pair(mimeType, childUri))
            }
        }

        val sortedItems = items.sortedWith(
            compareBy(
                { it.first != DocumentsContract.Document.MIME_TYPE_DIR },
                { it.second.toString().lowercase(Locale.getDefault()) }
            )
        )

        val sortedUris = mutableListOf<Uri>()
        sortedItems.forEach { (mimeType, uri) ->
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (includeDirectories) {
                    sortedUris.add(uri)
                }
                sortedUris.addAll(walkDownDirectory(uri, includeDirectories))
            } else {
                sortedUris.add(uri)
            }
        }

        return sortedUris
    }

    /**
     * Gets the filename from a [DocumentFileCompat].
     *
     * @return the name of the DocumentFile file
     */
    fun getFileName(docFile: DocumentFileCompat?): String? {
        if (docFile == null) {
            return null
        }

        val context = XmpApplication.instance!!.applicationContext
        return DocumentFileCompat.fromSingleUri(context, docFile.uri)?.name
    }

    /**
     * Gets the filename from a [Uri]
     *
     * @return the name of the uri file
     */
    fun getFileName(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        val context = XmpApplication.instance!!.applicationContext
        return DocumentFileCompat.fromSingleUri(context, uri)?.name
    }
}

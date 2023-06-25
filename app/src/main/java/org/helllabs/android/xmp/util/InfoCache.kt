package org.helllabs.android.xmp.util

import org.helllabs.android.xmp.PrefManager
import org.helllabs.android.xmp.Xmp
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

object InfoCache {

    @JvmStatic
    fun clearCache(filename: String): Boolean {
        val cacheFile = File(PrefManager.CACHE_DIR, "$filename.cache")
        val skipFile = File(PrefManager.CACHE_DIR, "$filename.skip")
        var ret = false
        if (cacheFile.isFile) {
            cacheFile.delete()
            ret = true
        }
        if (skipFile.isFile) {
            skipFile.delete()
            ret = true
        }
        return ret
    }

    private fun removeCacheDir(filename: String): Boolean {
        val cacheFile = File(PrefManager.CACHE_DIR, "$filename.cache")
        var ret = false
        if (cacheFile.isDirectory) {
            cacheFile.delete()
            ret = true
        }
        return ret
    }

    @JvmStatic
    fun delete(filename: String): Boolean {
        val file = File(filename)
        clearCache(filename)
        return file.delete()
    }

    @JvmStatic
    fun deleteRecursive(filename: String): Boolean {
        val file = File(filename)
        return if (file.isDirectory) {
            for (f in file.listFiles().orEmpty()) {
                if (f.isDirectory) {
                    deleteRecursive(f.path)
                } else {
                    f.delete()
                }
            }
            file.delete()
            removeCacheDir(filename)
            true
        } else {
            clearCache(filename)
            file.delete()
        }
    }

    @JvmStatic
    fun fileExists(filename: String): Boolean {
        val file = File(filename)
        if (file.isFile) {
            return true
        }
        clearCache(filename)
        return false
    }

    @JvmStatic
    fun testModuleForceIfInvalid(filename: String): Boolean {
        val skipFile = File(PrefManager.CACHE_DIR, "$filename.skip")
        if (skipFile.isFile) {
            skipFile.delete()
        }
        return testModule(filename)
    }

    @JvmStatic
    fun testModule(filename: String): Boolean {
        return testModule(filename, ModInfo())
    }

    @Throws(IOException::class)
    private fun checkIfCacheValid(file: File, cacheFile: File, info: ModInfo): Boolean {
        var ret = false
        val reader = BufferedReader(FileReader(cacheFile), 512)
        val line = reader.readLine()
        if (line != null) {
            try {
                val size = line.toInt()
                if (size.toLong() == file.length()) {
                    info.name = reader.readLine()
                    if (info.name != null) {
                        reader.readLine() // skip filename
                        info.type = reader.readLine()
                        if (info.type != null) {
                            ret = true
                        }
                    }
                }
            } catch (e: NumberFormatException) {
                // Someone had binary contents in the cache file, breaking parseInt()
                ret = false
            }
        }
        reader.close()
        return ret
    }

    private fun testModule(filename: String, info: ModInfo): Boolean {
        if (!PrefManager.CACHE_DIR.isDirectory && !PrefManager.CACHE_DIR.mkdirs()) {
            // Can't use cache
            return Xmp.testModule(filename, info)
        }
        val file = File(filename)
        val cacheFile = File(PrefManager.CACHE_DIR, "$filename.cache")
        val skipFile = File(PrefManager.CACHE_DIR, "$filename.skip")
        return try {
            // If cache file exists and size matches, file is mod
            if (cacheFile.isFile) {
                // If we have cache and skip, delete skip
                if (skipFile.isFile) {
                    skipFile.delete()
                }

                // Check if our cache data is good
                if (checkIfCacheValid(file, cacheFile, info)) {
                    return true
                }
                cacheFile.delete() // Invalid or outdated cache file
            }
            if (skipFile.isFile) {
                return false
            }
            val isMod = Xmp.testModule(filename, info)
            if (isMod) {
                val lines = arrayOf(
                    file.length().toString(),
                    info.name,
                    filename,
                    info.type
                )
                val dir: File = cacheFile.parentFile!!
                if (!dir.isDirectory) {
                    dir.mkdirs()
                }
                cacheFile.createNewFile()
                FileUtils.writeToFile(cacheFile, lines)
            } else {
                val dir: File = skipFile.parentFile!!
                if (!dir.isDirectory) {
                    dir.mkdirs()
                }
                skipFile.createNewFile()
            }
            isMod
        } catch (e: IOException) {
            Xmp.testModule(filename, info)
        }
    }
}

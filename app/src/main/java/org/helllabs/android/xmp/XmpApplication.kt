package org.helllabs.android.xmp

import android.app.Application
import android.util.Log
import org.helllabs.android.xmp.di.ModArchiveModule
import org.helllabs.android.xmp.di.ModArchiveModuleImpl
import timber.log.Timber

class XmpApplication : Application() {

    var fileList: MutableList<String>? = null

    override fun onCreate() {
        super.onCreate()
        setInstance(this)

        modArchiveModule = ModArchiveModuleImpl(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(
                object : Timber.Tree() {
                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        when (priority) {
                            Log.ERROR -> System.err.println("[ERROR]: $message")
                            Log.WARN -> System.err.println("[WARN]: $message")
                            else -> Unit // Ignore other log types.
                        }
                    }
                }
            )
        }

        PrefManager.init(applicationContext)
    }

    fun clearFileList() {
        fileList = null
    }

    companion object {

        lateinit var modArchiveModule: ModArchiveModule

        @get:Synchronized
        var instance: XmpApplication? = null
            private set

        private fun setInstance(instance: XmpApplication) {
            Companion.instance = instance
        }
    }
}

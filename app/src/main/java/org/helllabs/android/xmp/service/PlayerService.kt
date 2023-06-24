package org.helllabs.android.xmp.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import androidx.preference.PreferenceManager
import org.helllabs.android.xmp.Xmp
import org.helllabs.android.xmp.Xmp.deinit
import org.helllabs.android.xmp.Xmp.dropAudio
import org.helllabs.android.xmp.Xmp.endPlayer
import org.helllabs.android.xmp.Xmp.fillBuffer
import org.helllabs.android.xmp.Xmp.getComment
import org.helllabs.android.xmp.Xmp.getModName
import org.helllabs.android.xmp.Xmp.getModType
import org.helllabs.android.xmp.Xmp.getModVars
import org.helllabs.android.xmp.Xmp.getPlayer
import org.helllabs.android.xmp.Xmp.getVolume
import org.helllabs.android.xmp.Xmp.hasFreeBuffer
import org.helllabs.android.xmp.Xmp.init
import org.helllabs.android.xmp.Xmp.loadModule
import org.helllabs.android.xmp.Xmp.mute
import org.helllabs.android.xmp.Xmp.playAudio
import org.helllabs.android.xmp.Xmp.releaseModule
import org.helllabs.android.xmp.Xmp.restartAudio
import org.helllabs.android.xmp.Xmp.seek
import org.helllabs.android.xmp.Xmp.setPlayer
import org.helllabs.android.xmp.Xmp.setSequence
import org.helllabs.android.xmp.Xmp.setVolume
import org.helllabs.android.xmp.Xmp.startPlayer
import org.helllabs.android.xmp.Xmp.stopAudio
import org.helllabs.android.xmp.Xmp.stopModule
import org.helllabs.android.xmp.Xmp.time
import org.helllabs.android.xmp.preferences.Preferences
import org.helllabs.android.xmp.service.notifier.LollipopNotifier
import org.helllabs.android.xmp.service.notifier.Notifier
import org.helllabs.android.xmp.service.notifier.OreoNotifier
import org.helllabs.android.xmp.service.utils.QueueManager
import org.helllabs.android.xmp.service.utils.RemoteControl
import org.helllabs.android.xmp.service.utils.Watchdog
import org.helllabs.android.xmp.util.FileUtils.basename
import org.helllabs.android.xmp.util.InfoCache.delete
import org.helllabs.android.xmp.util.InfoCache.testModule
import org.helllabs.android.xmp.util.Log

class PlayerService : Service(), OnAudioFocusChangeListener {

    private val callbacks = RemoteCallbackList<PlayerCallback>()
    private var playAllSequences = false
    private var audioInitialized = false
    private var audioManager: AudioManager? = null
    private var canRelease = false
    private var cmd = 0
    private var discardBuffer = false // don't play current buffer if changing module while paused
    private var ducking = false
    private var currentFileName: String? = null // currently playing file
    private var hasAudioFocus = false
    private var looped = false
    private var notifier: Notifier? = null
    private var playThread: Thread? = null
    private lateinit var prefs: SharedPreferences
    private var previousPaused = false // save previous pause state
    private var queue: QueueManager? = null
    private var receiverHelper: ReceiverHelper? = null
    private var remoteControl: RemoteControl? = null
    private var restart = false
    private var sampleRate = 0
    private var sequenceNumber = 0
    private var startIndex = 0
    private var updateData = false
    private var volume = 0
    private var watchdog: Watchdog? = null

    var isPlayerPaused = false
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Create service")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        remoteControl = RemoteControl(this, audioManager)
        hasAudioFocus = requestAudioFocus()
        if (!hasAudioFocus) {
            Log.e(TAG, "Can't get audio focus")
        }
        receiverHelper = ReceiverHelper(this)
        receiverHelper!!.registerReceivers()
        var bufferMs = prefs.getInt(Preferences.BUFFER_MS, DEFAULT_BUFFER_MS)
        if (bufferMs < MIN_BUFFER_MS) {
            bufferMs = MIN_BUFFER_MS
        } else if (bufferMs > MAX_BUFFER_MS) {
            bufferMs = MAX_BUFFER_MS
        }
        sampleRate = prefs.getString(Preferences.SAMPLING_RATE, "44100")!!
            .toInt()
        if (init(sampleRate, bufferMs)) {
            audioInitialized = true
        } else {
            Log.e(TAG, "error initializing audio")
        }

        volume = getVolume()
        isAlive = false
        isLoaded = false
        isPlayerPaused = false
        playAllSequences = prefs.getBoolean(Preferences.ALL_SEQUENCES, false)

        notifier = if (Build.VERSION.SDK_INT >= 26) {
            OreoNotifier(this)
        } else {
            LollipopNotifier(this)
        }

        watchdog = Watchdog(10)
        watchdog!!.setOnTimeoutListener {
            Log.e(TAG, "Stopped by watchdog")
            audioManager!!.abandonAudioFocus(this@PlayerService)
            stopSelf()
        }
        watchdog!!.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        receiverHelper!!.unregisterReceivers()
        watchdog!!.stop()
        notifier!!.cancel()

        // session.setActive(false);
        if (audioInitialized) {
            end(if (hasAudioFocus) RESULT_OK else RESULT_NO_AUDIO_FOCUS)
        } else {
            end(RESULT_CANT_OPEN_AUDIO)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun requestAudioFocus(): Boolean {
        return audioManager!!.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun updateNotification() {
        if (queue != null) { // It seems that queue can be null if we're called from PhoneStateListener
            var name = getModName()
            if (name!!.isEmpty()) {
                name = basename(queue!!.filename)
            }
            notifier!!.notify(
                name,
                getModType(),
                queue!!.index,
                if (isPlayerPaused) Notifier.TYPE_PAUSE else 0
            )
        }
    }

    private fun doPauseAndNotify() {
        isPlayerPaused = isPlayerPaused xor true
        updateNotification()
        if (isPlayerPaused) {
            stopAudio()
            remoteControl!!.setStatePaused()
        } else {
            remoteControl!!.setStatePlaying()
            restartAudio()
        }
    }

    fun actionStop() {
        stopModule()
        if (isPlayerPaused) {
            doPauseAndNotify()
        }
        cmd = CMD_STOP
    }

    fun actionPlayPause() {
        doPauseAndNotify()

        // Notify clients that we paused
        val numClients = callbacks.beginBroadcast()
        for (i in 0 until numClients) {
            try {
                callbacks.getBroadcastItem(i).pauseCallback()
            } catch (e: RemoteException) {
                Log.e(TAG, "Error notifying pause to client")
            }
        }
        callbacks.finishBroadcast()
    }

    fun actionPrev() {
        if (time() > 2000) {
            seek(0)
        } else {
            stopModule()
            cmd = CMD_PREV
        }
        if (isPlayerPaused) {
            doPauseAndNotify()
            discardBuffer = true
        }
    }

    fun actionNext() {
        stopModule()
        if (isPlayerPaused) {
            doPauseAndNotify()
            discardBuffer = true
        }
        cmd = CMD_NEXT
    }

    private fun notifyNewSequence() {
        val numClients = callbacks.beginBroadcast()
        for (j in 0 until numClients) {
            try {
                callbacks.getBroadcastItem(j).newSequenceCallback()
            } catch (e: RemoteException) {
                Log.e(TAG, "Error notifying end of module to client")
            }
        }
        callbacks.finishBroadcast()
    }

    private inner class PlayRunnable : Runnable {
        override fun run() {
            cmd = CMD_NONE
            val vars = IntArray(8)
            remoteControl!!.setStatePlaying()
            var lastRecognized = 0
            do {
                currentFileName = queue?.filename // Used in reconnection

                // If this file is unrecognized, and we're going backwards, go to previous
                // If we're at the start of the list, go to the last recognized file
                if (currentFileName == null || !testModule(currentFileName!!)) {
                    Log.w(TAG, "$currentFileName: unrecognized format")
                    if (cmd == CMD_PREV) {
                        if (queue!!.index <= 0) {
                            queue!!.index =
                                lastRecognized - 1 // -1 because we have queue.next() in the while condition
                            continue
                        }
                        queue!!.previous()
                    }
                    continue
                }

                // Set default pan before we load the module
                val defpan = prefs.getInt(Preferences.DEFAULT_PAN, 50)
                Log.i(TAG, "Set default pan to $defpan")
                setPlayer(Xmp.PLAYER_DEFPAN, defpan)

                // Ditto if we can't load the module
                Log.w(TAG, "Load $currentFileName")
                if (loadModule(currentFileName) < 0) {
                    Log.e(TAG, "Error loading $currentFileName")
                    if (cmd == CMD_PREV) {
                        if (queue!!.index <= 0) {
                            queue!!.index = lastRecognized - 1
                            continue
                        }
                        queue!!.previous()
                    }
                    continue
                }
                lastRecognized = queue!!.index
                cmd = CMD_NONE
                var name = getModName()
                if (name!!.isEmpty()) {
                    name = basename(currentFileName)
                }
                notifier!!.notify(
                    name,
                    getModType(),
                    queue!!.index,
                    Notifier.TYPE_TICKER
                )
                isLoaded = true

                // Unmute all channels
                for (i in 0..63) {
                    mute(i, 0)
                }
                val volBoost = prefs.getString(Preferences.VOL_BOOST, "1")
                val interpTypes =
                    intArrayOf(Xmp.INTERP_NEAREST, Xmp.INTERP_LINEAR, Xmp.INTERP_SPLINE)
                val temp = prefs.getString(Preferences.INTERP_TYPE, "1")!!
                    .toInt()
                var interpType: Int
                interpType = if (temp in 1..2) {
                    interpTypes[temp]
                } else {
                    Xmp.INTERP_LINEAR
                }
                if (!prefs.getBoolean(Preferences.INTERPOLATE, true)) {
                    interpType = Xmp.INTERP_NEAREST
                }
                startPlayer(sampleRate)
                synchronized(audioManager!!) {
                    if (ducking) {
                        setPlayer(Xmp.PLAYER_VOLUME, DUCK_VOLUME)
                    }
                }
                var numClients = callbacks.beginBroadcast()
                for (j in 0 until numClients) {
                    try {
                        callbacks.getBroadcastItem(j).newModCallback()
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Error notifying new module to client")
                    }
                }
                callbacks.finishBroadcast()
                setPlayer(Xmp.PLAYER_AMP, volBoost!!.toInt())
                setPlayer(Xmp.PLAYER_MIX, prefs.getInt(Preferences.STEREO_MIX, 100))
                setPlayer(Xmp.PLAYER_INTERP, interpType)
                setPlayer(Xmp.PLAYER_DSP, Xmp.DSP_LOWPASS)
                var flags = getPlayer(Xmp.PLAYER_CFLAGS)
                flags = if (prefs.getBoolean(Preferences.AMIGA_MIXER, false)) {
                    flags or Xmp.FLAGS_A500
                } else {
                    flags and Xmp.FLAGS_A500.inv()
                }
                setPlayer(Xmp.PLAYER_CFLAGS, flags)
                updateData = true
                sequenceNumber = 0
                var playNewSequence: Boolean
                setSequence(sequenceNumber)
                playAudio()
                Log.i(TAG, "Enter play loop")
                do {
                    getModVars(vars)
                    remoteControl!!.setMetadata(getModName(), getModType(), vars[0].toLong())
                    while (cmd == CMD_NONE) {
                        discardBuffer = false

                        // Wait if paused
                        while (isPlayerPaused) {
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                break
                            }
                            watchdog!!.refresh()
                            receiverHelper!!.checkReceivers()
                        }
                        if (discardBuffer) {
                            Log.d(TAG, "discard buffer")
                            dropAudio()
                            break
                        }

                        // Wait if no buffers available
                        while (!hasFreeBuffer() && !isPlayerPaused && cmd == CMD_NONE) {
                            try {
                                Thread.sleep(40)
                            } catch (e: InterruptedException) {
                            }
                        }

                        // Fill a new buffer
                        if (fillBuffer(looped) < 0) {
                            break
                        }
                        watchdog!!.refresh()
                        receiverHelper!!.checkReceivers()
                    }

                    // Subsong explorer
                    // Do all this if we've exited normally and explorer is active
                    playNewSequence = false
                    if (playAllSequences && cmd == CMD_NONE) {
                        sequenceNumber++
                        Log.w(TAG, "Play sequence $sequenceNumber")
                        if (setSequence(sequenceNumber)) {
                            playNewSequence = true
                            notifyNewSequence()
                        }
                    }
                } while (playNewSequence)
                endPlayer()
                isLoaded = false

                // notify end of module to our clients
                numClients = callbacks.beginBroadcast()
                if (numClients > 0) {
                    canRelease = false
                    for (j in 0 until numClients) {
                        try {
                            Log.w(TAG, "Call end of module callback")
                            callbacks.getBroadcastItem(j).endModCallback()
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Error notifying end of module to client")
                        }
                    }
                    callbacks.finishBroadcast()

                    // if we have clients, make sure we can release module
                    var timeout = 0
                    try {
                        while (!canRelease && timeout < 20) {
                            Thread.sleep(100)
                            timeout++
                        }
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Sleep interrupted: $e")
                    }
                } else {
                    callbacks.finishBroadcast()
                }
                Log.w(TAG, "Release module")
                releaseModule()

                // audio.stop();

                // Used when current files are replaced by a new set
                if (restart) {
                    Log.i(TAG, "Restart")
                    queue!!.index = startIndex - 1
                    cmd = CMD_NONE
                    restart = false
                } else if (cmd == CMD_PREV) {
                    queue!!.previous()
                    // returnToPrev = false;
                }
            } while (cmd != CMD_STOP && queue!!.next())
            synchronized(playThread!!) {
                updateData = false // stop getChannelData update
            }
            watchdog!!.stop()
            notifier!!.cancel()
            remoteControl!!.setStateStopped()
            audioManager!!.abandonAudioFocus(this@PlayerService)

            // end();
            Log.i(TAG, "Stop service")
            stopSelf()
        }
    }

    private fun end(result: Int) {
        Log.i(TAG, "End service")
        val numClients = callbacks.beginBroadcast()
        for (i in 0 until numClients) {
            try {
                callbacks.getBroadcastItem(i).endPlayCallback(result)
            } catch (e: RemoteException) {
                Log.e(TAG, "Error notifying end of play to client")
            }
        }
        callbacks.finishBroadcast()
        isAlive = false
        stopModule()
        if (isPlayerPaused) {
            doPauseAndNotify()
        }
        deinit()
        // audio.release();
    }

    private val binder: ModInterface.Stub = object : ModInterface.Stub() {
        override fun play(
            fileList: MutableList<String>,
            start: Int,
            shuffle: Boolean,
            loopList: Boolean,
            keepFirst: Boolean
        ) {
            if (!audioInitialized || !hasAudioFocus) {
                stopSelf()
                return
            }
            queue = QueueManager(fileList, start, shuffle, loopList, keepFirst)
            notifier!!.setQueue(queue)
            // notifier.clean();
            cmd = CMD_NONE
            if (isPaused) {
                doPauseAndNotify()
            }
            if (isAlive) {
                Log.i(TAG, "Use existing player thread")
                restart = true
                startIndex = if (keepFirst) 0 else start
                nextSong()
            } else {
                Log.i(TAG, "Start player thread")
                playThread = Thread(PlayRunnable())
                playThread!!.start()
            }
            isAlive = true
        }

        override fun add(fileList: List<String>) {
            queue!!.add(fileList)
            updateNotification()
            // notifier.notification("Added to play queue");			
        }

        override fun stop() {
            actionStop()
        }

        override fun pause() {
            doPauseAndNotify()
            receiverHelper?.isHeadsetPaused = false
        }

        override fun getInfo(values: IntArray) {
            Xmp.getInfo(values)
        }

        override fun seek(seconds: Int) {
            Xmp.seek(seconds)
        }

        override fun time(): Int {
            return Xmp.time()
        }

        override fun getModVars(vars: IntArray) {
            Xmp.getModVars(vars)
        }

        override fun getModName(): String {
            return Xmp.getModName()!!
        }

        override fun getModType(): String {
            return Xmp.getModType()!!
        }

        override fun getChannelData(
            volumes: IntArray,
            finalvols: IntArray,
            pans: IntArray,
            instruments: IntArray,
            keys: IntArray,
            periods: IntArray
        ) {
            if (updateData) {
                synchronized(playThread!!) {
                    Xmp.getChannelData(
                        volumes,
                        finalvols,
                        pans,
                        instruments,
                        keys,
                        periods
                    )
                }
            }
        }

        override fun getSampleData(
            trigger: Boolean,
            ins: Int,
            key: Int,
            period: Int,
            chn: Int,
            width: Int,
            buffer: ByteArray
        ) {
            if (updateData) {
                synchronized(playThread!!) {
                    Xmp.getSampleData(
                        trigger,
                        ins,
                        key,
                        period,
                        chn,
                        width,
                        buffer
                    )
                }
            }
        }

        override fun nextSong() {
            stopModule()
            cmd = CMD_NEXT
            if (isPaused) {
                doPauseAndNotify()
            }
            discardBuffer = true
        }

        override fun prevSong() {
            stopModule()
            cmd = CMD_PREV
            if (isPaused) {
                doPauseAndNotify()
            }
            discardBuffer = true
        }

        @Throws(RemoteException::class)
        override fun toggleLoop(): Boolean {
            looped = looped xor true
            return looped
        }

        @Throws(RemoteException::class)
        override fun toggleAllSequences(): Boolean {
            playAllSequences = allSequences xor true
            return playAllSequences
        }

        @Throws(RemoteException::class)
        override fun getLoop(): Boolean {
            return looped
        }

        @Throws(RemoteException::class)
        override fun getAllSequences(): Boolean {
            return playAllSequences
        }

        override fun isPaused(): Boolean {
            return isPlayerPaused
        }

        override fun setSequence(seq: Int): Boolean {
            val ret = Xmp.setSequence(seq)
            if (ret) {
                sequenceNumber = seq
                notifyNewSequence()
            }
            return ret
        }

        override fun allowRelease() {
            canRelease = true
        }

        override fun getSeqVars(vars: IntArray) {
            Xmp.getSeqVars(vars)
        }

        // for Reconnection
        override fun getFileName(): String {
            return currentFileName.orEmpty()
        }

        override fun getInstruments(): Array<String?>? {
            return Xmp.getInstruments()
        }

        override fun getPatternRow(
            pat: Int,
            row: Int,
            rowNotes: ByteArray,
            rowInstruments: ByteArray
        ) {
            if (isAlive) {
                Xmp.getPatternRow(pat, row, rowNotes, rowInstruments)
            }
        }

        override fun mute(chn: Int, status: Int): Int {
            return Xmp.mute(chn, status)
        }

        override fun hasComment(): Boolean {
            return getComment() != null
        }

        // File management
        override fun deleteFile(): Boolean {
            Log.i(TAG, "Delete file $fileName")
            return delete(fileName)
        }

        // Callback
        override fun registerCallback(callback: PlayerCallback?) {
            if (callback != null) {
                callbacks.register(callback)
            }
        }

        override fun unregisterCallback(callback: PlayerCallback?) {
            if (callback != null) {
                callbacks.unregister(callback)
            }
        }
    }

    // for audio focus loss
    private fun autoPause(pause: Boolean): Boolean {
        Log.i(
            TAG,
            "Auto pause changed to " + pause + ", previously " + receiverHelper!!.isAutoPaused
        )

        if (pause) {
            previousPaused = isPlayerPaused
            receiverHelper!!.isAutoPaused = true
            isPlayerPaused = false // set to complement, flip on doPause()
            doPauseAndNotify()
        } else {
            if (receiverHelper!!.isAutoPaused && !receiverHelper!!.isHeadsetPaused) {
                receiverHelper!!.isAutoPaused = false
                isPlayerPaused = !previousPaused // set to complement, flip on doPause()
                doPauseAndNotify()
            }
        }
        return receiverHelper!!.isAutoPaused
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT")
                // Pause playback
                autoPause(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                // Lower volume
                synchronized(audioManager!!) {
                    volume = getVolume()
                    setVolume(DUCK_VOLUME)
                    ducking = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.w(TAG, "AUDIOFOCUS_GAIN")
                // Resume playback/raise volume
                autoPause(false)
                synchronized(audioManager!!) {
                    setVolume(volume)
                    ducking = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS")
                // Stop playback
                actionStop()
            }
            else -> {}
        }
    }

    companion object {
        private const val TAG = "PlayerService"

        const val RESULT_OK = 0
        const val RESULT_CANT_OPEN_AUDIO = 1
        const val RESULT_NO_AUDIO_FOCUS = 2

        private const val CMD_NONE = 0
        private const val CMD_NEXT = 1
        private const val CMD_PREV = 2
        private const val CMD_STOP = 3
        private const val MIN_BUFFER_MS = 80
        private const val MAX_BUFFER_MS = 1000
        private const val DEFAULT_BUFFER_MS = 400
        private const val DUCK_VOLUME = 0x500

        var isAlive = false
        var isLoaded = false
    }
}

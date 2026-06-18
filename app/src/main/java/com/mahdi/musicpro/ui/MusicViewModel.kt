package com.mahdi.musicpro.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mahdi.musicpro.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

import android.provider.MediaStore
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

enum class RepeatMode {
    OFF, ALL, ONE
}

data class EqualizerState(
    val bass: Float = 0.5f,
    val treble: Float = 0.5f,
    val vocal: Float = 0.5f,
    val spatial3d: Float = 0.3f,
    val selectedPreset: String = "Normal"
)

data class EarphonesState(
    val dolbyEnabled: Boolean = true,
    val dolbyProfile: String = "Music",
    val uhqEnabled: Boolean = false,
    val tubeBoost: Float = 0.6f
)

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val equalizer: EqualizerState = EqualizerState(),
    val sleepTimerMinutesLeft: Int? = null,
    val sleepTimerSecondsLeft: Int? = null,
    val selectedSleepTimerMinutes: Int? = null,
    val activeTab: Int = 0, // 0: Tracks, 1: Playlists, 2: Albums, 3: Artists, 4: Favorites
    val searchQuery: String = "",
    val audioQuality: String = "High",
    val crossfadeSeconds: Int = 0, // 0 is Off
    val simulatedCacheSizeMb: Double = 14.8,
    val hasMediaPermission: Boolean = false,
    val isEqualizerSupported: Boolean = true,
    val brokenTrackIds: Set<String> = emptySet(),
    val shouldExpandPlayer: Boolean = false
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private var instanceRef: java.lang.ref.WeakReference<MusicViewModel>? = null
        private var strongInstance: MusicViewModel? = null

        var instance: MusicViewModel?
            get() {
                return instanceRef?.get()
            }
            set(value) {
                instanceRef = value?.let { java.lang.ref.WeakReference(it) }
                updateStrongReference()
            }

        fun updateStrongReference() {
            if (com.mahdi.musicpro.MusicPlaybackService.isActive()) {
                strongInstance = instanceRef?.get()
            } else {
                strongInstance = null
            }
        }
    }

    private val database = MusicDatabase.getDatabase(application)
    private val repository = MusicRepository(database.musicDao())

    private var mediaPlayer: MediaPlayer? = null
    private var equalizerFx: android.media.audiofx.Equalizer? = null
    private var bassBoostFx: android.media.audiofx.BassBoost? = null
    private var virtualizerFx: android.media.audiofx.Virtualizer? = null
    private var loudnessEnhancerFx: android.media.audiofx.LoudnessEnhancer? = null
    private var presetReverbFx: android.media.audiofx.PresetReverb? = null

    private var progressPollJob: Job? = null
    private var isProgressPollingActive = false
    private var sleepCountDownTimer: CountDownTimer? = null
    private var playbackJob: Job? = null
    private var isMediaPlayerPrepared = false
    private var errorRetryCount = 0
    private var playWhenReady = false
    var wasPlayingBeforeFocusLoss = false
    private var playlistJob: Job? = null

    // True Shuffle implementation state
    private var shuffleIndices = emptyList<Int>()
    private var shuffleCurrentPoint = -1

    private var volumeAnimatorJob: Job? = null
    private var currentVolume = 1f
    private var isFadingOut = false

    private fun fadeInVolume(durationSecs: Int) {
        volumeAnimatorJob?.cancel()
        if (durationSecs <= 0) {
            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
            currentVolume = 1f
            return
        }
        volumeAnimatorJob = viewModelScope.launch {
            val steps = durationSecs * 10
            val delayTime = 100L
            for (i in 1..steps) {
                val vol = i.toFloat() / steps
                try { mediaPlayer?.setVolume(vol, vol) } catch (_: Exception) {}
                currentVolume = vol
                delay(delayTime)
            }
            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
            currentVolume = 1f
        }
    }

    private fun fadeOutAndNext(durationSecs: Int) {
        if (isFadingOut) return
        isFadingOut = true
        volumeAnimatorJob?.cancel()
        if (durationSecs <= 0) {
            handleTrackCompletion()
            isFadingOut = false
            return
        }
        volumeAnimatorJob = viewModelScope.launch {
            val steps = durationSecs * 10
            val delayTime = 100L
            val startVol = currentVolume
            for (i in 1..steps) {
                val vol = startVol * (1f - (i.toFloat() / steps))
                try { mediaPlayer?.setVolume(vol, vol) } catch (_: Exception) {}
                currentVolume = vol
                delay(delayTime)
            }
            try { mediaPlayer?.setVolume(0f, 0f) } catch (_: Exception) {}
            isFadingOut = false
            handleTrackCompletion()
        }
    }

    private fun fadeOutAndPause(durationSecs: Int) {
        if (isFadingOut) return
        isFadingOut = true
        volumeAnimatorJob?.cancel()
        if (durationSecs <= 0) {
            pausePlayback()
            isFadingOut = false
            return
        }
        volumeAnimatorJob = viewModelScope.launch {
            val steps = durationSecs * 10
            val delayTime = 100L
            val startVol = currentVolume
            for (i in 1..steps) {
                val vol = startVol * (1f - (i.toFloat() / steps))
                try { mediaPlayer?.setVolume(vol, vol) } catch (_: Exception) {}
                currentVolume = vol
                delay(delayTime)
            }
            try { mediaPlayer?.setVolume(0f, 0f) } catch (_: Exception) {}
            isFadingOut = false
            pausePlayback()
            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
            currentVolume = 1f
        }
    }

    var hasLoadedOnce = false
    private var mediaObserver: android.database.ContentObserver? = null

    // UI state flows
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _earphonesState = MutableStateFlow(EarphonesState())
    val earphonesState: StateFlow<EarphonesState> = _earphonesState.asStateFlow()

    // Room driven flows
    private val deletedTrackIds = MutableStateFlow<Set<String>>(emptySet())

    val allTracks: StateFlow<List<Track>> = combine(repository.allTracksFlow, deletedTrackIds) { tracks, deletedIds ->
        tracks.filterNot { it.id in deletedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackLibrary.originalTracks)

    val favorites: StateFlow<List<Track>> = combine(repository.favoritesFlow, deletedTrackIds) { tracks, deletedIds ->
        tracks.filterNot { it.id in deletedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylistTracks: StateFlow<List<PlaylistTrackEntity>> = repository.allPlaylistTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playHistory: StateFlow<List<Track>> = combine(repository.recentTracksFlow, deletedTrackIds) { tracks, deletedIds ->
        tracks.filterNot { it.id in deletedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks observed in active selected playlist
    private val _activePlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val activePlaylistTracks: StateFlow<List<Track>> = combine(_activePlaylistTracks, deletedTrackIds) { tracks, deletedIds ->
        tracks.filterNot { it.id in deletedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    private val _pendingDeleteSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingDeleteSender: StateFlow<android.content.IntentSender?> = _pendingDeleteSender.asStateFlow()

    fun clearPendingDeleteSender() {
        _pendingDeleteSender.value = null
    }

    fun deleteTracks(trackIds: List<String>) {
        deletedTrackIds.value = deletedTrackIds.value + trackIds
        val state = _uiState.value
        val updatedQueue = state.queue.filterNot { it.id in trackIds }

        if (state.currentTrack != null && trackIds.contains(state.currentTrack.id)) {
            if (updatedQueue.isEmpty()) {
                stopPlayback()
                _uiState.update {
                    it.copy(
                        queue = emptyList(),
                        queueIndex = -1,
                        currentTrack = null,
                        isPlaying = false
                    )
                }
            } else {
                val nextIndex = if (state.queueIndex >= updatedQueue.size) {
                    updatedQueue.size - 1
                } else {
                    state.queueIndex
                }
                _uiState.update {
                    it.copy(
                        queue = updatedQueue,
                        queueIndex = nextIndex,
                        currentTrack = updatedQueue.getOrNull(nextIndex)
                    )
                }
                if (state.isPlaying) {
                    if (nextIndex >= 0) {
                        playTrackAtIndex(nextIndex)
                    } else {
                        stopPlayback()
                    }
                }
            }
        } else {
            val currentTrackId = state.currentTrack?.id
            val newIndex = updatedQueue.indexOfFirst { it.id == currentTrackId }
            _uiState.update {
                it.copy(
                    queue = updatedQueue,
                    queueIndex = newIndex
                )
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val uris = trackIds.mapNotNull { id ->
                try {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong())
                } catch (_: Exception) {
                    null
                }
            }
            if (uris.isEmpty()) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                    _pendingDeleteSender.value = pendingIntent.intentSender
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error creating delete request on API 30+", e)
                }
            } else {
                for (uri in uris) {
                    try {
                        app.contentResolver.delete(uri, null, null)
                    } catch (securityException: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is android.app.RecoverableSecurityException) {
                            val intentSender = securityException.userAction.actionIntent.intentSender
                            _pendingDeleteSender.value = intentSender
                        } else {
                            Log.e("MusicViewModel", "Failed to delete track uri: $uri", securityException)
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to delete track uri: $uri", e)
                    }
                }
            }
        }
    }

    init {
        instance = this

        // Check if there is a previously played track saved in preferences
        val prefs = application.getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
        val lastTrackId = prefs.getString("last_track_id", null)
        val savedShuffle = prefs.getBoolean("last_shuffle_mode", false)
        val savedRepeatStr = prefs.getString("last_repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        val savedRepeat = try { RepeatMode.valueOf(savedRepeatStr) } catch(_: Exception) { RepeatMode.OFF }

        // Init the initial queue with all tracks from the library. 
        // If we have a saved track, start currentTrack as null so restoreQueueFromPrefs() will load it correctly!
        _uiState.update { it.copy(
            queue = TrackLibrary.tracks, 
            queueIndex = 0, 
            currentTrack = if (lastTrackId != null) null else TrackLibrary.tracks.firstOrNull(),
            shuffleMode = savedShuffle,
            repeatMode = savedRepeat
        ) }
        loadTracksFromDevice()

        // Background check for unreachable/broken tracks (highly optimized)
        _uiState.update { it.copy(brokenTrackIds = emptySet()) }

        // Sync track edits persistently from Room
        var lastScannedTrackIds = emptySet<String>()
        viewModelScope.launch {
            allTracks.collect { tracksList ->
                val editMap = tracksList.associateBy { it.id }
                val currentScannedIds = tracksList.map { it.id }.toSet()
                _uiState.update { state ->
                    val queueIds = state.queue.map { it.id }.toSet()
                    val hasOnlyDemo = state.queue.isNotEmpty() && state.queue.all { it.id.startsWith("demo_") }
                    val realScannedListExists = tracksList.isNotEmpty() && tracksList.any { !it.id.startsWith("demo_") }

                    val queueWasAllTracks = queueIds.isNotEmpty() &&
                            queueIds.size == lastScannedTrackIds.size &&
                            lastScannedTrackIds.all { queueIds.contains(it) }

                    val finalQueue = if (state.queue.isEmpty() || (hasOnlyDemo && realScannedListExists) || queueWasAllTracks) {
                        tracksList
                    } else {
                        val filteredQueue = state.queue.filter { q -> editMap.containsKey(q.id) }
                        filteredQueue.map { track ->
                            editMap[track.id] ?: track
                        }
                    }
                    val updatedCurrent = state.currentTrack?.let { current ->
                        editMap[current.id]
                    }
                    val finalCurrent = updatedCurrent ?: tracksList.firstOrNull()
                    val finalIndex = if (finalCurrent != null) finalQueue.indexOfFirst { it.id == finalCurrent.id } else -1

                    state.copy(
                        queue = finalQueue,
                        currentTrack = finalCurrent,
                        queueIndex = finalIndex
                    )
                }
                lastScannedTrackIds = currentScannedIds
            }
        }

        // Sync playback state with MusicPlaybackService automatically
        viewModelScope.launch {
            _uiState
                .map { state -> Pair(state.currentTrack, state.isPlaying) }
                .distinctUntilChanged()
                .collect { (currentTrack, isPlaying) ->
                    // فقط اگه سرویس از قبل فعاله آپدیت کن
                    // هرگز از اینجا سرویس جدید start نکن
                    val activeService = com.mahdi.musicpro.MusicPlaybackService.activeService
                    if (activeService != null) {
                        activeService.updatePlayback(currentTrack, isPlaying, _progressMs.value)
                    }
                    // اگه activeService == null است، هیچکاری نکن
                    // سرویس وقتی کاربر Play میزنه از togglePlayback/resumePlayback start میشه
                }
        }

        // Keep shuffle indices synchronized with any queue updates
        viewModelScope.launch {
            _uiState
                .map { it.queue }
                .distinctUntilChanged()
                .collect { queue ->
                    if (_uiState.value.shuffleMode) {
                        generateShuffleIndices()
                    }
                }
        }

        // Register ContentObserver to listen for media changes on the device (new tracks added, deleted, etc.)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        mediaObserver = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                super.onChange(selfChange, uri)
                android.util.Log.d("MusicViewModel", "Media library revised, reloading: $uri")
                loadTracksFromDevice()
            }
        }
        try {
            application.contentResolver.registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver!!
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to register content observer", e)
        }
    }

    private fun releaseAudioEffects() {
        try { equalizerFx?.release() } catch (_: Exception) {}
        try { bassBoostFx?.release() } catch (_: Exception) {}
        try { virtualizerFx?.release() } catch (_: Exception) {}
        try { loudnessEnhancerFx?.release() } catch (_: Exception) {}
        try { presetReverbFx?.release() } catch (_: Exception) {}
        equalizerFx = null
        bassBoostFx = null
        virtualizerFx = null
        loudnessEnhancerFx = null
        presetReverbFx = null
    }

    private fun setEqBandValue(band: Short, value: Float) {
        val eq = equalizerFx ?: return
        try {
            val range = eq.bandLevelRange
            if (range != null && range.size >= 2) {
                val minLevel = range[0]
                val maxLevel = range[1]
                val level = if (value < 0.5f) {
                    (minLevel + (value / 0.5f) * (-minLevel)).toInt().toShort()
                } else {
                    ((value - 0.5f) / 0.5f * maxLevel).toInt().toShort()
                }
                eq.setBandLevel(band, level)
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error setting EQ band $band to $value", e)
        }
    }

    private fun applyAudioEffectsSettings() {
        val eq = _uiState.value.equalizer
        val earphones = _earphonesState.value
        
        setEqBandValue(0, eq.bass)
        setEqBandValue(1, eq.bass)
        setEqBandValue(2, eq.vocal)
        setEqBandValue(3, eq.treble)
        setEqBandValue(4, eq.treble)
        
        // BassBoost
        try {
            if (bassBoostFx?.strengthSupported == true) {
                val strength = (eq.bass * 2f - 1f).coerceIn(0f, 1f)
                val scaled = (strength * 1000f).toInt().toShort()
                bassBoostFx?.setStrength(scaled)
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error setting BassBoost strength", e)
        }
        
        // Dolby Atmos Spatializer via Virtualizer
        try {
            if (virtualizerFx?.strengthSupported == true) {
                val spatialVal = if (earphones.dolbyEnabled) {
                    when (earphones.dolbyProfile) {
                        "Movie" -> 1.0f
                        "Music" -> 0.85f
                        "Voice" -> 0.60f
                        else -> 0.75f // "Auto"
                    }
                } else {
                    eq.spatial3d.coerceIn(0f, 1f)
                }
                val scaled = (spatialVal * 1000f).toInt().coerceIn(0, 1000).toShort()
                virtualizerFx?.setStrength(scaled)
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error setting Virtualizer strength", e)
        }
        
        // Dolby Atmos Spatializer via PresetReverb
        try {
            val reverb = presetReverbFx
            if (reverb != null) {
                if (earphones.dolbyEnabled) {
                    reverb.enabled = true
                    val preset = when (earphones.dolbyProfile) {
                        "Movie" -> android.media.audiofx.PresetReverb.PRESET_LARGEHALL
                        "Music" -> android.media.audiofx.PresetReverb.PRESET_LARGEROOM
                        "Voice" -> android.media.audiofx.PresetReverb.PRESET_SMALLROOM
                        else -> android.media.audiofx.PresetReverb.PRESET_MEDIUMHALL // "Auto"
                    }
                    reverb.preset = preset
                    mediaPlayer?.attachAuxEffect(reverb.id)
                    mediaPlayer?.setAuxEffectSendLevel(1.0f)
                } else {
                    reverb.preset = android.media.audiofx.PresetReverb.PRESET_NONE
                    reverb.enabled = false
                    mediaPlayer?.setAuxEffectSendLevel(0.0f)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error setting Dolby Atmos PresetReverb", e)
        }
        
        // UHQ Upscaler Enhancer via LoudnessEnhancer
        try {
            val enhancer = loudnessEnhancerFx
            if (enhancer != null) {
                if (earphones.uhqEnabled) {
                    enhancer.enabled = true
                    // +7.0 dB boost provides an amazing high-fidelity gain without distortion
                    enhancer.setTargetGain(700)
                } else {
                    // Maintain a warm, robust +2.5 dB baseline gain to prevent volume loss when turned off
                    enhancer.enabled = true
                    enhancer.setTargetGain(250)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error setting LoudnessEnhancer gain", e)
        }
    }

    private fun releaseMediaPlayer() {
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        isMediaPlayerPrepared = false
        if (oldPlayer != null) {
            try {
                oldPlayer.setOnPreparedListener(null)
                oldPlayer.setOnCompletionListener(null)
                oldPlayer.setOnErrorListener(null)
            } catch (_: Exception) {}
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    oldPlayer.reset()
                    oldPlayer.release()
                } catch (_: Exception) {}
            }
        }
        releaseAudioEffects()
    }

    private fun setupMediaPlayer() {
        if (mediaPlayer != null) {
            return
        }
        try {
            releaseAudioEffects()
            val state = _uiState.value
            mediaPlayer = MediaPlayer().apply {
                val audioAttributesBuilder = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                setAudioAttributes(audioAttributesBuilder.build())
                setWakeMode(getApplication(), android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnPreparedListener {
                    isMediaPlayerPrepared = true
                    errorRetryCount = 0
                    val savedProgress = _progressMs.value
                    _uiState.update { it.copy(isBuffering = false, durationMs = this.duration.toLong()) }
                    if (savedProgress > 0L) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                this.seekTo(savedProgress, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                @Suppress("DEPRECATION")
                                this.seekTo(savedProgress.toInt())
                            }
                        } catch (_: Exception) {}
                    }
                    // فقط اگه کاربر قبلاً play زده بود start کن
                    if (_uiState.value.isPlaying) {
                        startPlayback()
                    } else {
                        // اگه pause است، فقط notification رو بدون start آپدیت کن
                        val track = _uiState.value.currentTrack
                        if (track != null) {
                            // فقط اگه سرویس از قبل فعاله notification آپدیت بشه
                            val svc = com.mahdi.musicpro.MusicPlaybackService.activeService
                            svc?.let {
                                com.mahdi.musicpro.MusicPlaybackService.updatePlaybackState(
                                    getApplication(), track, false, savedProgress
                                )
                            }
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicViewModel", "خطا در مدیاپلیر: what=$what extra=$extra")
                    isMediaPlayerPrepared = false
                    _uiState.update { it.copy(isBuffering = false, isPlaying = false) }
                    errorRetryCount++
                    if (errorRetryCount <= 2) {
                        val s = _uiState.value
                        if (s.currentTrack != null && s.queueIndex >= 0 && s.queue.isNotEmpty()) {
                            playTrackAtIndex(s.queueIndex, keepProgress = true)
                        }
                    } else {
                        errorRetryCount = 0
                        viewModelScope.launch(Dispatchers.Main) {
                            val current = _uiState.value.currentTrack
                            val msg = if (current?.id?.startsWith("demo_") == true) {
                                "خطا در پخش دمو. لطفاً اتصال اینترنت یا فیلترشکن خود را بررسی کرده یا دسترسی فایل برای اسکن آهنگ‌های گوشی را تایید کنید."
                            } else {
                                "خطا در پخش آهنگ"
                            }
                            android.widget.Toast.makeText(
                                getApplication(),
                                msg,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    true
                }
            }

            // Bind audio effects to MediaPlayer session id asynchronously on a background thread to prevent UI freezing
            val sessionId = mediaPlayer?.audioSessionId ?: 0
            if (sessionId != 0) {
                viewModelScope.launch(Dispatchers.Default) {
                    var eqSupported = true
                    var tempEq: android.media.audiofx.Equalizer? = null
                    var tempBass: android.media.audiofx.BassBoost? = null
                    var tempVirt: android.media.audiofx.Virtualizer? = null
                    var tempLoud: android.media.audiofx.LoudnessEnhancer? = null
                    var tempReverb: android.media.audiofx.PresetReverb? = null

                    try {
                        tempEq = android.media.audiofx.Equalizer(0, sessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        Log.w("MusicViewModel", "Equalizer creation failed (not supported on this device): ${t.message}")
                        eqSupported = false
                    }
                    try {
                        tempBass = android.media.audiofx.BassBoost(0, sessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        Log.w("MusicViewModel", "BassBoost creation failed (not supported on this device): ${t.message}")
                    }
                    try {
                        tempVirt = android.media.audiofx.Virtualizer(0, sessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        Log.w("MusicViewModel", "Virtualizer creation failed (not supported on this device): ${t.message}")
                    }
                    try {
                        tempLoud = android.media.audiofx.LoudnessEnhancer(sessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        Log.w("MusicViewModel", "LoudnessEnhancer creation failed: ${t.message}")
                    }
                    try {
                        tempReverb = android.media.audiofx.PresetReverb(0, sessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        Log.w("MusicViewModel", "PresetReverb creation failed: ${t.message}")
                    }

                    withContext(Dispatchers.Main) {
                        equalizerFx = tempEq
                        bassBoostFx = tempBass
                        virtualizerFx = tempVirt
                        loudnessEnhancerFx = tempLoud
                        presetReverbFx = tempReverb
                        _uiState.update { it.copy(isEqualizerSupported = eqSupported) }
                        applyAudioEffectsSettings()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "خطا در آماده‌سازی مدیاپلیر", e)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTab = index) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Playback functions
    fun playTrack(track: Track) {
        val state = _uiState.value
        var foundIndex = state.queue.indexOfFirst { it.id == track.id }
        
        if (foundIndex == -1) {
            // If track is not in queue, insert it after current track and play it
            val newQueue = state.queue.toMutableList()
            val insertPos = if (state.queueIndex == -1) 0 else state.queueIndex + 1
            newQueue.add(insertPos, track)
            foundIndex = insertPos
            _uiState.update { it.copy(queue = newQueue) }
        }

        playTrackAtIndex(foundIndex)
    }

    fun playTrackAtIndex(index: Int, keepProgress: Boolean = false, startImmediately: Boolean = true) {
        val state = _uiState.value
        if (index < 0 || index >= state.queue.size) return
        
        // Prevent duplicate calls to the same track when it is currently buffering
        if (state.isBuffering && index == state.queueIndex) return

        val track = state.queue[index]
        if (state.brokenTrackIds.contains(track.id)) {
            android.widget.Toast.makeText(getApplication(), "فایل این آهنگ یافت نشد یا حذف شده است.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        playWhenReady = startImmediately
        if (startImmediately) {
            ensureServiceStarted()
        }

        if (!keepProgress) {
            _progressMs.value = 0L
        }
        _uiState.update {
            it.copy(
                currentTrack = track,
                queueIndex = index,
                isBuffering = true,
                isPlaying = startImmediately
            )
        }
        saveQueueToPrefs()

        // Align with True Shuffle state
        if (_uiState.value.shuffleMode) {
            val point = shuffleIndices.indexOf(index)
            if (point != -1) {
                shuffleCurrentPoint = point
            } else {
                generateShuffleIndices()
            }
        }

        // Record to Room history
        viewModelScope.launch {
            repository.markTrackPlayed(track.id)
        }

        isMediaPlayerPrepared = false
        // Cancel previous loading job to safely manage player preparation
        val previousJob = playbackJob
        previousJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                stopProgressPolling()
                
                // Release the previous player first to build a clean one
                releaseMediaPlayer()
                
                // Initialize the fresh MediaPlayer
                setupMediaPlayer()
                
                val player = mediaPlayer
                if (player != null) {
                    val uri = android.net.Uri.parse(track.contentUri)
                    if (track.contentUri.startsWith("content://") || track.contentUri.startsWith("file://")) {
                        player.setDataSource(getApplication(), uri)
                    } else {
                        player.setDataSource(track.contentUri)
                    }
                    player.prepareAsync() // Invokes OnPreparedListener asynchronously
                } else {
                    Log.e("MusicViewModel", "خطا: مدیاپلیر مقداردهی اولیه نشد")
                    _uiState.update { it.copy(isBuffering = false, isPlaying = false) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "خطا در پخش آدرس محتوا: ${track.contentUri}", e)
                _uiState.update { it.copy(isBuffering = false, isPlaying = false) }
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "امکان پخش این آهنگ وجود ندارد. رفتن به آهنگ بعدی...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    skipNext()
                }
            }
        }
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (state.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
        saveQueueToPrefs()
    }

    fun pausePlayback() {
        val player = mediaPlayer
        if (player != null) {
            val state = _uiState.value
            if (state.isPlaying) {
                try {
                    player.pause()
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "خطا در متوقف ساختن موقت آهنگ", e)
                }
                _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                stopProgressPolling()
            }
        } else {
            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
            stopProgressPolling()
        }
        saveQueueToPrefs()
    }

    fun stopPlayback() {
        val player = mediaPlayer
        if (player != null) {
            try {
                player.stop()
            } catch (_: Exception) {}
        }
        isMediaPlayerPrepared = false
        _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
        stopProgressPolling()
    }

    fun setVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (_: Exception) {}
        currentVolume = volume
    }

    fun setWillPlayWhenReady(value: Boolean) {
        _uiState.update { it.copy(isPlaying = value) }
    }

    private fun ensureServiceStarted() {
        if (com.mahdi.musicpro.MusicPlaybackService.activeService == null) {
            val intent = android.content.Intent(getApplication(), com.mahdi.musicpro.MusicPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<android.app.Application>().startForegroundService(intent)
            } else {
                getApplication<android.app.Application>().startService(intent)
            }
        }
    }

    fun resumePlayback() {
        ensureServiceStarted()
        val state = _uiState.value
        if (state.currentTrack != null) {
            var player = mediaPlayer
            if (player == null) {
                setupMediaPlayer()
                player = mediaPlayer
            }
            if (!state.isPlaying) {
                if (player != null && isMediaPlayerPrepared && !state.isBuffering) {
                    try {
                        player.start()
                        _uiState.update { it.copy(isPlaying = true, isBuffering = false) }
                        startProgressPolling()
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "خطا در اجرای پخش آهنگ: ${e.message}", e)
                        if (state.queueIndex >= 0 && state.queue.isNotEmpty()) {
                            playTrackAtIndex(state.queueIndex, keepProgress = true)
                        }
                    }
                } else if (state.isBuffering) {
                    // آهنگ داره لود میشه، فقط isPlaying=true ست کن
                    _uiState.update { it.copy(isPlaying = true) }
                } else {
                    if (state.queueIndex >= 0 && state.queue.isNotEmpty()) {
                        playTrackAtIndex(state.queueIndex, keepProgress = true)
                    }
                }
            }
        } else if (state.queue.isNotEmpty()) {
            playTrackAtIndex(0)
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        isFadingOut = false
        try {
            if (isMediaPlayerPrepared) {
                mediaPlayer?.start()
                _uiState.update { it.copy(isPlaying = true, isBuffering = false) }
                startProgressPolling()
                fadeInVolume(state.crossfadeSeconds)
            } else {
                _uiState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "خطا در شروع پخش آهنگ: ${e.message}", e)
            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
        }
    }

    private fun generateShuffleIndices() {
        val queueSize = _uiState.value.queue.size
        if (queueSize <= 0) {
            shuffleIndices = emptyList()
            shuffleCurrentPoint = -1
            return
        }
        
        val indices = (0 until queueSize).toMutableList()
        val currentIdx = _uiState.value.queueIndex
        
        if (currentIdx in 0 until queueSize) {
            indices.remove(currentIdx)
            indices.shuffle()
            indices.add(0, currentIdx)
            shuffleIndices = indices
            shuffleCurrentPoint = 0
        } else {
            indices.shuffle()
            shuffleIndices = indices
            shuffleCurrentPoint = 0
        }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (state.shuffleMode) {
            if (shuffleIndices.size != state.queue.size || shuffleIndices.isEmpty()) {
                generateShuffleIndices()
            }
            if (shuffleIndices.isNotEmpty()) {
                val nextPoint = (shuffleCurrentPoint + 1) % shuffleIndices.size
                shuffleCurrentPoint = nextPoint
                val targetIndex = shuffleIndices[nextPoint]
                playTrackAtIndex(targetIndex)
            } else {
                val nextIndex = (state.queueIndex + 1) % state.queue.size
                playTrackAtIndex(nextIndex)
            }
        } else {
            val nextIndex = (state.queueIndex + 1) % state.queue.size
            playTrackAtIndex(nextIndex)
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (_progressMs.value > 3000L) {
            seekTo(0L)
            return
        }

        if (state.shuffleMode) {
            if (shuffleIndices.size != state.queue.size || shuffleIndices.isEmpty()) {
                generateShuffleIndices()
            }
            if (shuffleIndices.isNotEmpty()) {
                val prevPoint = if (shuffleCurrentPoint <= 0) shuffleIndices.size - 1 else shuffleCurrentPoint - 1
                shuffleCurrentPoint = prevPoint
                val targetIndex = shuffleIndices[prevPoint]
                playTrackAtIndex(targetIndex)
            } else {
                val prevIndex = if (state.queueIndex <= 0) state.queue.size - 1 else state.queueIndex - 1
                playTrackAtIndex(prevIndex)
            }
        } else {
            val prevIndex = if (state.queueIndex <= 0) state.queue.size - 1 else state.queueIndex - 1
            playTrackAtIndex(prevIndex)
        }
    }

    fun toggleShuffle() {
        val newState = !_uiState.value.shuffleMode
        _uiState.update { it.copy(shuffleMode = newState) }
        if (newState) {
            generateShuffleIndices()
        }
        val prefs = getApplication<Application>()
            .getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("last_shuffle_mode", newState).apply()
    }

    fun cycleRepeatMode() {
        _uiState.update { state ->
            val nextMode = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            val prefs = getApplication<Application>()
                .getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("last_repeat_mode", nextMode.name).apply()
            state.copy(repeatMode = nextMode)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer?.seekTo(positionMs.toInt())
            }
        } catch (_: Exception) {}
        _progressMs.value = positionMs
        saveQueueToPrefs()
        val state = _uiState.value
        com.mahdi.musicpro.MusicPlaybackService.updatePlaybackState(
            getApplication(),
            state.currentTrack,
            state.isPlaying,
            positionMs
        )
    }

    private fun handleTrackCompletion() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                // Play same track again
                playTrackAtIndex(state.queueIndex)
            }
            RepeatMode.ALL -> {
                skipNext()
            }
            RepeatMode.OFF -> {
                if (state.shuffleMode) {
                    if (shuffleIndices.size != state.queue.size || shuffleIndices.isEmpty()) {
                        generateShuffleIndices()
                    }
                    if (shuffleIndices.isNotEmpty() && shuffleCurrentPoint < shuffleIndices.size - 1) {
                        skipNext()
                    } else {
                        _progressMs.value = 0L
                        _uiState.update { it.copy(isPlaying = false) }
                        stopProgressPolling()
                    }
                } else {
                    if (state.queueIndex < state.queue.size - 1) {
                        skipNext()
                    } else {
                        // Last track completed, stop.
                        _progressMs.value = 0L
                        _uiState.update { it.copy(isPlaying = false) }
                        stopProgressPolling()
                    }
                }
            }
        }
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        isProgressPollingActive = true
        progressPollJob = viewModelScope.launch {
            var lastSaveTime = 0L
            while (isProgressPollingActive) {
                delay(500)
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            val currentPos = player.currentPosition.toLong()
                            _progressMs.value = currentPos
                            com.mahdi.musicpro.MusicPlaybackService.updateSessionPlaybackStateOnly(getApplication(), true, currentPos)
                            
                            val systemTime = System.currentTimeMillis()
                            if (systemTime - lastSaveTime > 5000L) {
                                saveQueueToPrefs()
                                lastSaveTime = systemTime
                            }

                            val state = _uiState.value
                            val duration = state.durationMs
                            val crossfadeSecs = state.crossfadeSeconds
                            if (crossfadeSecs > 0 && duration > 0 && !isFadingOut) {
                                val timeRemainingMs = duration - currentPos
                                if (timeRemainingMs <= crossfadeSecs * 1000L) {
                                    fadeOutAndNext(crossfadeSecs)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopProgressPolling() {
        isProgressPollingActive = false
        progressPollJob?.cancel()
        progressPollJob = null
    }

    // Favorite management
    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
        }
    }

    fun isTrackFavorite(trackId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(repository.isFavorite(trackId))
        }
    }

    // Playlist management
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun renamePlaylist(playlistId: Int, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = newName)
            }
        }
    }

    fun updatePlaylistOrder(playlistId: Int, displayOrder: Int) {
        viewModelScope.launch {
            repository.updatePlaylistOrder(playlistId, displayOrder)
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
                _activePlaylistTracks.value = emptyList()
                playlistJob?.cancel()
                playlistJob = null
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch {
            repository.getTracksForPlaylist(playlist.id).collect { tracksList ->
                _activePlaylistTracks.value = tracksList
            }
        }
    }

    fun deselectPlaylist() {
        _selectedPlaylist.value = null
        _activePlaylistTracks.value = emptyList()
        playlistJob?.cancel()
        playlistJob = null
    }

    fun addTrackToPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    // Queue utilities
    fun clearQueue() {
        _uiState.update { it.copy(queue = emptyList(), queueIndex = -1, currentTrack = null, isPlaying = false) }
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        isMediaPlayerPrepared = false
        stopProgressPolling()
    }

    fun addToQueue(track: Track) {
        _uiState.update { state ->
            val updatedQueue = state.queue.toMutableList()
            updatedQueue.add(track)
            val newIndex = if (state.currentTrack != null) {
                if (state.queueIndex >= 0 && state.queueIndex < state.queue.size && state.queue[state.queueIndex].id == state.currentTrack.id) {
                    state.queueIndex
                } else {
                    updatedQueue.indexOfFirst { it.id == state.currentTrack.id }
                }
            } else {
                0
            }
            state.copy(queue = updatedQueue, queueIndex = if (newIndex >= 0) newIndex else 0)
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        _uiState.update { state ->
            val updatedQueue = state.queue.toMutableList()
            if (from in updatedQueue.indices && to in updatedQueue.indices) {
                val item = updatedQueue.removeAt(from)
                updatedQueue.add(to, item)
            }
            val foundIndex = state.currentTrack?.let { current -> updatedQueue.indexOfFirst { it.id == current.id } } ?: -1
            val finalIndex = if (foundIndex >= 0) foundIndex else if (state.queueIndex >= 0 && state.queueIndex < updatedQueue.size) state.queueIndex else 0
            val finalTrack = if (foundIndex >= 0) state.currentTrack else if (updatedQueue.isNotEmpty()) updatedQueue[finalIndex] else null
            state.copy(queue = updatedQueue, queueIndex = finalIndex, currentTrack = finalTrack)
        }
    }

    // Equalizer controls
    fun updateEqualizerFreq(band: String, value: Float) {
        _uiState.update { state ->
            val eq = state.equalizer
            val updatedEq = when (band) {
                "Bass" -> eq.copy(bass = value, selectedPreset = "Custom")
                "Treble" -> eq.copy(treble = value, selectedPreset = "Custom")
                "Vocal" -> eq.copy(vocal = value, selectedPreset = "Custom")
                "Spatial" -> eq.copy(spatial3d = value, selectedPreset = "Custom")
                else -> eq
            }
            state.copy(equalizer = updatedEq)
        }
        applyAudioEffectsSettings()
    }

    fun applyEqualizerPreset(preset: String) {
        val updatedEq = when (preset) {
            "Normal" -> EqualizerState(0.5f, 0.5f, 0.5f, 0.3f, "Normal")
            "Pop" -> EqualizerState(0.6f, 0.7f, 0.4f, 0.4f, "Pop")
            "Classic" -> EqualizerState(0.4f, 0.5f, 0.6f, 0.2f, "Classic")
            "Jazz" -> EqualizerState(0.5f, 0.4f, 0.7f, 0.3f, "Jazz")
            "Rock" -> EqualizerState(0.8f, 0.6f, 0.5f, 0.5f, "Rock")
            else -> EqualizerState(0.5f, 0.5f, 0.5f, 0.3f, "Normal")
        }
        _uiState.update { it.copy(equalizer = updatedEq) }
        applyAudioEffectsSettings()
    }

    fun applyEarphonesEffects(dolbyAtmos: Boolean, dolbyProfile: String, uhqUpscaler: Boolean, tubeBoost: Float) {
        _earphonesState.value = EarphonesState(
            dolbyEnabled = dolbyAtmos,
            dolbyProfile = dolbyProfile,
            uhqEnabled = uhqUpscaler,
            tubeBoost = tubeBoost
        )
        val targetBass = if (tubeBoost > 0f) tubeBoost else 0.5f
        val targetTreble = if (uhqUpscaler) 0.78f else 0.55f
        val targetSpatial = if (dolbyAtmos) {
            when (dolbyProfile) {
                "Music" -> 0.8f
                "Movie" -> 0.9f
                "Voice" -> 0.6f
                else -> 0.7f
            }
        } else {
            0.1f
        }
        
        _uiState.update { state ->
            val eq = state.equalizer.copy(
                bass = targetBass,
                treble = targetTreble,
                spatial3d = targetSpatial
            )
            state.copy(equalizer = eq)
        }
        applyAudioEffectsSettings()
    }

    fun resetEarphonesSettings() {
        _earphonesState.value = EarphonesState()
        applyEarphonesEffects(
            dolbyAtmos = true,
            dolbyProfile = "Music",
            uhqUpscaler = false,
            tubeBoost = 0.6f
        )
    }

    // Sleep timer controls
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()

        val totalMs = minutes * 60 * 1000L
        _uiState.update {
            it.copy(
                sleepTimerMinutesLeft = minutes,
                sleepTimerSecondsLeft = 0,
                selectedSleepTimerMinutes = minutes
            )
        }

        sleepCountDownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSecs = (millisUntilFinished / 1000).toInt()
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                _uiState.update {
                    it.copy(
                        sleepTimerMinutesLeft = mins,
                        sleepTimerSecondsLeft = secs
                    )
                }
            }

            override fun onFinish() {
                _uiState.update {
                    it.copy(
                        sleepTimerMinutesLeft = null,
                        sleepTimerSecondsLeft = null,
                        selectedSleepTimerMinutes = null
                    )
                }
                fadeOutAndPause(3)
            }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepCountDownTimer?.cancel()
        sleepCountDownTimer = null
        _uiState.update {
            it.copy(
                sleepTimerMinutesLeft = null,
                sleepTimerSecondsLeft = null,
                selectedSleepTimerMinutes = null
            )
        }
    }

    fun updateTrackInfo(trackId: String, title: String, artist: String, album: String) {
        viewModelScope.launch {
            repository.saveTrackEdit(trackId, title, artist, album)
        }
    }

    fun removeCurrentTrackFromQueue() {
        val state = _uiState.value
        val currentTrack = state.currentTrack ?: return
        
        val updatedQueue = state.queue.toMutableList()
        val currentIndex = state.queueIndex
        
        val targetIndex = updatedQueue.indexOfFirst { it.id == currentTrack.id }
        if (targetIndex != -1) {
            updatedQueue.removeAt(targetIndex)
        }
        
        if (updatedQueue.isEmpty()) {
            _uiState.update { it.copy(queue = emptyList(), queueIndex = -1, currentTrack = null, isPlaying = false) }
            try {
                mediaPlayer?.stop()
            } catch (_: Exception) {}
            stopProgressPolling()
        } else {
            val nextIndex = if (currentIndex >= updatedQueue.size) 0 else currentIndex
            _uiState.update { it.copy(queue = updatedQueue) }
            playTrackAtIndex(nextIndex)
        }
    }

    fun dismissPlayer() {
        try {
            mediaPlayer?.pause()
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e("MusicViewModel", "خطا در بستن و متوقف کردن پلیر", e)
        }
        isMediaPlayerPrepared = false
        stopProgressPolling()
        _uiState.update { it.copy(currentTrack = null, isPlaying = false) }
    }

    fun setQueue(tracksList: List<Track>) {
        _uiState.update { it.copy(queue = tracksList, queueIndex = 0) }
    }

    // Settings adjustments
    fun setAudioQuality(quality: String) {
        _uiState.update { it.copy(audioQuality = quality) }
    }

    fun setCrossfade(seconds: Int) {
        _uiState.update { it.copy(crossfadeSeconds = seconds) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().cacheDir?.deleteRecursively()
                getApplication<Application>().externalCacheDir?.deleteRecursively()
            } catch (_: Exception) {}
        }
        _uiState.update { it.copy(simulatedCacheSizeMb = 0.0) }
    }

    fun triggerPlayerExpansion() {
        _uiState.update { it.copy(shouldExpandPlayer = true) }
    }

    fun onPlayerExpansionHandled() {
        _uiState.update { it.copy(shouldExpandPlayer = false) }
    }

    fun loadTracksFromDevice() {
        hasLoadedOnce = true
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(hasMediaPermission = hasPermission) }
            }

            if (!hasPermission) {
                withContext(Dispatchers.Main) {
                    repository.deviceTracksFlow.value = emptyList()
                }
                return@launch
            }

            val trackList = mutableListOf<Track>()
            val existingFilePaths = mutableSetOf<String>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
            )
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"

            val gradients = listOf(
                "#FF416C" to "#FF4B2B",
                "#1F1C2C" to "#928DAB",
                "#11998e" to "#38ef7d",
                "#FC466B" to "#3F5EFB",
                "#00c6ff" to "#0072ff",
                "#f12711" to "#f5af19"
            )
            val defaultTitle = getApplication<Application>().getString(com.mahdi.musicpro.R.string.unknown_title)
            val defaultArtist = getApplication<Application>().getString(com.mahdi.musicpro.R.string.unknown_artist)
            val defaultAlbum = getApplication<Application>().getString(com.mahdi.musicpro.R.string.unknown_album)
            val defaultGenre = getApplication<Application>().getString(com.mahdi.musicpro.R.string.local_audio)

            try {
                app.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    var count = 0
                    while (cursor.moveToNext()) {
                        kotlinx.coroutines.yield()
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: defaultTitle
                        val artist = cursor.getString(artistCol) ?: defaultArtist
                        val album = cursor.getString(albumCol) ?: defaultAlbum
                        val duration = cursor.getLong(durationCol)
                        val albumId = cursor.getLong(albumIdCol)
                        val dateAdded = cursor.getLong(dateAddedCol)
                        val filePath = cursor.getString(dataCol) ?: ""

                        if (filePath.isNotEmpty()) {
                            existingFilePaths.add(filePath)
                        }

                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val artworkUriBase = Uri.parse("content://media/external/audio/albumart")
                        val albumArtUri = ContentUris.withAppendedId(artworkUriBase, albumId).toString()

                        val grad = gradients[count % gradients.size]
                        trackList.add(
                            Track(
                                id = id.toString(),
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                contentUri = contentUri,
                                genre = defaultGenre,
                                coverGradientStart = grad.first,
                                coverGradientEnd = grad.second,
                                lyrics = emptyList(),
                                albumArtUri = albumArtUri,
                                dateAddedSecs = dateAdded
                            )
                        )
                        count++
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error reading MediaStore", e)
            }

            // Also search manual directories to find newly downloaded files instantly!
            try {
                val dirsToScan = mutableListOf<java.io.File>()
                try {
                    dirsToScan.add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS))
                } catch (_: Exception) {}
                try {
                    dirsToScan.add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC))
                } catch (_: Exception) {}
                try {
                    val extStorage = android.os.Environment.getExternalStorageDirectory()
                    dirsToScan.add(java.io.File(extStorage, "Download"))
                    dirsToScan.add(java.io.File(extStorage, "Music"))
                    dirsToScan.add(java.io.File(extStorage, "Telegram/Telegram Audio"))
                    dirsToScan.add(java.io.File(extStorage, "Telegram/Telegram Documents"))
                } catch (_: Exception) {}
                try {
                    app.getExternalFilesDir(null)?.let { dirsToScan.add(it) }
                    app.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.let { dirsToScan.add(it) }
                    app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let { dirsToScan.add(it) }
                    dirsToScan.add(app.filesDir)
                    app.cacheDir?.let { dirsToScan.add(it) }
                } catch (_: Exception) {}

                for (dir in dirsToScan.distinctBy { it.absolutePath }) {
                    scanDirectoryForMusic(
                        dir,
                        trackList,
                        existingFilePaths,
                        gradients,
                        defaultTitle,
                        defaultArtist,
                        defaultAlbum,
                        defaultGenre
                    )
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error scanning direct files", e)
            }

            // Sort the final merged list by date added descending (newest files absolutely first!)
            trackList.sortByDescending { it.dateAddedSecs }

            withContext(Dispatchers.Main) {
                val wasNull = _uiState.value.currentTrack == null
                repository.deviceTracksFlow.value = trackList
                // Populate initial queue/track if none is selected
                _uiState.update { state ->
                    val sourceTracks = if (trackList.isEmpty()) TrackLibrary.demoTracks else trackList
                    val updatedQueue = if (state.queue.isEmpty()) sourceTracks else state.queue
                    val updatedCurrent = if (state.currentTrack == null) sourceTracks.firstOrNull() else state.currentTrack
                    val updatedIndex = if (state.queueIndex == -1 && sourceTracks.isNotEmpty()) 0 else state.queueIndex
                    state.copy(
                        queue = updatedQueue,
                        currentTrack = updatedCurrent,
                        queueIndex = updatedIndex
                    )
                }
                if (wasNull) {
                    restoreQueueFromPrefs()
                }
            }
        }
    }

    private suspend fun scanDirectoryForMusic(
        dir: java.io.File,
        trackList: MutableList<Track>,
        existingFilePaths: Set<String>,
        gradients: List<Pair<String, String>>,
        defaultTitle: String,
        defaultArtist: String,
        defaultAlbum: String,
        defaultGenre: String
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        val mediaExtensions = setOf("mp3", "m4a", "wav", "ogg", "flac")
        var count = trackList.size

        for (file in files) {
            kotlinx.coroutines.yield()
            try {
                if (file.isFile) {
                    val path = file.absolutePath
                    if (path in existingFilePaths) continue
                    val ext = file.extension.lowercase()
                    if (ext in mediaExtensions) {
                        val fileUri = android.net.Uri.fromFile(file).toString()
                        val id = "file_" + path.hashCode()
                        val title = file.nameWithoutExtension
                        val dateAdded = file.lastModified() / 1000L

                        var duration = 0L
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(path)
                            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durStr?.toLongOrNull() ?: 0L
                            retriever.release()
                        } catch (_: Exception) {}

                        val grad = gradients[count % gradients.size]
                        trackList.add(
                            Track(
                                id = id,
                                title = title,
                                artist = defaultArtist,
                                album = defaultAlbum,
                                durationMs = duration,
                                contentUri = fileUri,
                                genre = defaultGenre,
                                coverGradientStart = grad.first,
                                coverGradientEnd = grad.second,
                                lyrics = emptyList(),
                                albumArtUri = null,
                                dateAddedSecs = dateAdded
                            )
                        )
                        count++
                    }
                } else if (file.isDirectory) {
                    val name = file.name.lowercase()
                    if (name != "android" && !name.startsWith(".")) {
                        scanDirectoryForMusic(file, trackList, existingFilePaths, gradients, defaultTitle, defaultArtist, defaultAlbum, defaultGenre)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun cleanUpAndRelease() {
        try {
            saveQueueToPrefs()
        } catch (_: Exception) {}
        mediaObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            mediaObserver = null
        }
        if (instance == this) {
            instance = null
        }
        cancelSleepTimer()
        releaseAudioEffects()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error releasing player", e)
            }
        }
        mediaPlayer = null
        stopProgressPolling()
    }

    fun saveQueueToPrefs() {
        val state = _uiState.value
        val track = state.currentTrack ?: return
        val prefs = getApplication<Application>()
            .getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_track_id", track.id)
            .putInt("last_queue_index", state.queueIndex)
            .putLong("last_progress_ms", _progressMs.value)
            .putBoolean("last_was_playing", state.isPlaying)
            .apply()
    }

    private fun restoreQueueFromPrefs() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
        val lastTrackId = prefs.getString("last_track_id", null) ?: return
        val lastIndex = prefs.getInt("last_queue_index", 0)
        val lastProgress = prefs.getLong("last_progress_ms", 0L)
        val lastWasPlaying = prefs.getBoolean("last_was_playing", false)
        viewModelScope.launch {
            val tracks = try {
                kotlinx.coroutines.withTimeout(1500) {
                    allTracks.first { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                val currentDeviceTracks = allTracks.value
                if (currentDeviceTracks.isNotEmpty()) currentDeviceTracks else (TrackLibrary.tracks + TrackLibrary.demoTracks)
            }

            val track = tracks.find { it.id == lastTrackId }
                ?: (TrackLibrary.tracks + TrackLibrary.demoTracks).find { it.id == lastTrackId }
                ?: return@launch

            val finalQueue = if (tracks.any { it.id == track.id }) tracks else (TrackLibrary.tracks + TrackLibrary.demoTracks)
            val finalIndex = finalQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

            _uiState.update { state ->
                state.copy(
                    queue = finalQueue,
                    queueIndex = finalIndex,
                    currentTrack = track,
                    isPlaying = false,    // همیشه false — کاربر باید دستی play کنه
                    isBuffering = false
                )
            }
            _progressMs.value = lastProgress
        }
    }

    override fun onCleared() {
        super.onCleared()
        // If the playback service is still active, preserve this ViewModel instance and player state
        if (com.mahdi.musicpro.MusicPlaybackService.isActive()) {
            return
        }
        cleanUpAndRelease()
    }
}

class MusicViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        val existing = MusicViewModel.instance
        if (existing != null && com.mahdi.musicpro.MusicPlaybackService.isActive()) {
            @Suppress("UNCHECKED_CAST")
            return existing as T
        }
        existing?.cleanUpAndRelease()
        val newInstance = MusicViewModel(application)
        MusicViewModel.instance = newInstance
        @Suppress("UNCHECKED_CAST")
        return newInstance as T
    }
}

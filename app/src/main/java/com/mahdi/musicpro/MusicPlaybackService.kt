package com.mahdi.musicpro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.mahdi.musicpro.data.Track
import com.mahdi.musicpro.ui.MusicViewModel
import kotlinx.coroutines.*

class MusicPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private var currentTrack: Track? = null
    private var isPlaying: Boolean = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastUpdateJob: Job? = null

    private var audioManager: AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                MusicViewModel.instance?.pausePlayback()
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                MusicViewModel.instance?.let { vm ->
                    vm.wasPlayingBeforeFocusLoss = false
                    if (vm.uiState.value.isPlaying || vm.uiState.value.isBuffering) {
                        vm.pausePlayback()
                    }
                }
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                MusicViewModel.instance?.let { vm ->
                    if (vm.uiState.value.isPlaying || vm.uiState.value.isBuffering) {
                        vm.wasPlayingBeforeFocusLoss = true
                        vm.pausePlayback()
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MusicViewModel.instance?.let { vm ->
                    if (vm.uiState.value.isPlaying || vm.uiState.value.isBuffering) {
                        vm.wasPlayingBeforeFocusLoss = true
                        vm.pausePlayback()
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                MusicViewModel.instance?.let { vm ->
                    vm.setVolume(1.0f)
                    if (vm.wasPlayingBeforeFocusLoss) {
                        vm.wasPlayingBeforeFocusLoss = false
                        if (!vm.uiState.value.isBuffering) {
                            vm.resumePlayback()
                        } else {
                            vm.setWillPlayWhenReady(true)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this

        createNotificationChannel()
        val tempNotification = buildTempNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, tempNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, tempNotification)
        }

        if (com.mahdi.musicpro.ui.MusicViewModel.instance == null) {
            try {
                com.mahdi.musicpro.ui.MusicViewModelFactory(application).create(com.mahdi.musicpro.ui.MusicViewModel::class.java)
            } catch (e: Exception) {
                Log.e("MusicPlaybackService", "Failed to initialize MusicViewModel in onCreate", e)
            }
        }
        com.mahdi.musicpro.ui.MusicViewModel.updateStrongReference()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                becomingNoisyReceiver,
                android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                becomingNoisyReceiver,
                android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        }

        mediaSession = MediaSessionCompat(this, "SMusicPlaybackService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MusicViewModel.instance?.resumePlayback()
                }
                override fun onPause() {
                    MusicViewModel.instance?.pausePlayback()
                }
                override fun onSkipToNext() {
                    MusicViewModel.instance?.skipNext()
                }
                override fun onSkipToPrevious() {
                    MusicViewModel.instance?.skipPrevious()
                }
                override fun onSeekTo(pos: Long) {
                    MusicViewModel.instance?.seekTo(pos)
                }
            })
        }
        sessionToken = mediaSession.sessionToken
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (com.mahdi.musicpro.ui.MusicViewModel.instance == null) {
            try {
                com.mahdi.musicpro.ui.MusicViewModelFactory(application).create(com.mahdi.musicpro.ui.MusicViewModel::class.java)
            } catch (e: Exception) {
                Log.e("MusicPlaybackService", "Failed to initialize MusicViewModel in onStartCommand", e)
            }
        }

        // ✅ حذف شد: updatePlayback خودکار در onStartCommand
        // این باعث loop بود چون هر بار سرویس start میشد، updatePlayback صدا میزد
        // که requestAudioFocus جدید میساخت و AUDIOFOCUS_LOSS ایجاد میکرد

        if (intent != null) {
            val action = intent.action
            if (action == Intent.ACTION_MEDIA_BUTTON) {
                androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
                return START_STICKY
            }
            if (action != null) {
                when (action) {
                    ACTION_PLAY -> MusicViewModel.instance?.resumePlayback()
                    ACTION_PAUSE -> MusicViewModel.instance?.pausePlayback()
                    ACTION_PREVIOUS -> MusicViewModel.instance?.skipPrevious()
                    ACTION_NEXT -> MusicViewModel.instance?.skipNext()
                    ACTION_STOP -> stopForegroundService()
                    ACTION_DISMISS -> {
                        MusicViewModel.instance?.let { vm ->
                            if (vm.uiState.value.isPlaying || vm.uiState.value.isBuffering) {
                                vm.pausePlayback()
                            } else {
                                stopForegroundCompat(true)
                            }
                        }
                    }
                }
            } else {
                val trackId = intent.getStringExtra("extra_track_id")
                val title = intent.getStringExtra("extra_track_title")
                val artist = intent.getStringExtra("extra_track_artist")
                val album = intent.getStringExtra("extra_track_album")
                val duration = intent.getLongExtra("extra_track_duration", 0L)
                val uriStr = intent.getStringExtra("extra_track_uri")
                val artStr = intent.getStringExtra("extra_track_art")
                val isPlayingExtra = intent.getBooleanExtra("extra_is_playing", false)
                val progress = intent.getLongExtra("extra_progress", 0L)

                if (trackId != null && title != null && artist != null) {
                    val dummyTrack = Track(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = album ?: "",
                        durationMs = duration,
                        contentUri = uriStr ?: "",
                        genre = "",
                        coverGradientStart = "",
                        coverGradientEnd = "",
                        lyrics = emptyList(),
                        albumArtUri = artStr
                    )
                    updatePlayback(dummyTrack, isPlayingExtra, progress)
                }
            }
        }
        return START_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            val result = audioManager?.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus = result
            result
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus = result
            result
        }
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun updatePlayback(track: Track?, isPlaying: Boolean, progressMs: Long) {
        this.currentTrack = track
        this.isPlaying = isPlaying

        try {
            val cacheFile = java.io.File(cacheDir, "widget_art_cache.jpg")
            if (cacheFile.exists()) cacheFile.delete()
        } catch (e: Exception) {}

        if (track == null) {
            com.mahdi.musicpro.widget.MusicProWidgetProvider.updateWidgetDirectly(
                this, getString(R.string.now_playing_no_track), "", false, null
            )
            abandonAudioFocus()
            stopForegroundCompat(true)
            stopSelf()
            return
        }

        // ✅ فقط وقتی isPlaying=true و focus نداریم، request کن
        if (isPlaying && !hasAudioFocus) {
            val focusGained = requestAudioFocus()
            if (!focusGained) {
                MusicViewModel.instance?.pausePlayback()
                return
            }
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                progressMs, 1.0f
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        com.mahdi.musicpro.widget.MusicProWidgetProvider.updateWidgetDirectly(
            this, track.title, track.artist, isPlaying, track.albumArtUri
        )

        lastUpdateJob?.cancel()

        val initialMetadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
        mediaSession.setMetadata(initialMetadataBuilder.build())

        val initialNotification = buildMediaNotification(track, isPlaying, null)
        val mainNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mainNotificationManager.notify(NOTIFICATION_ID, initialNotification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        lastUpdateJob = serviceScope.launch {
            val artBitmap = withContext(Dispatchers.IO) {
                val bitmap = getAlbumArtBitmap(track.albumArtUri)
                val cacheFile = java.io.File(cacheDir, "widget_art_cache.jpg")
                if (bitmap != null) {
                    try {
                        java.io.FileOutputStream(cacheFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    } catch (e: Exception) {}
                } else {
                    try { if (cacheFile.exists()) cacheFile.delete() } catch (e: Exception) {}
                }
                bitmap
            }
            if (artBitmap != null && currentTrack?.id == track.id) {
                val finalMetadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                mediaSession.setMetadata(finalMetadataBuilder.build())
                val updatedNotification = buildMediaNotification(track, isPlaying, artBitmap)
                mainNotificationManager.notify(NOTIFICATION_ID, updatedNotification)
            }

            com.mahdi.musicpro.widget.MusicProWidgetProvider.updateWidgetDirectly(
                this@MusicPlaybackService, track.title, track.artist, isPlaying, null
            )
        }
    }

    private fun getAlbumArtBitmap(albumArtUriStr: String?): Bitmap? {
        if (albumArtUriStr.isNullOrEmpty()) return null
        return try {
            val uri = android.net.Uri.parse(albumArtUriStr)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(300, 300), null)
            } else {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val fd = pfd.fileDescriptor
                    val bitmap = BitmapFactory.decodeFileDescriptor(fd)
                    pfd.close()
                    bitmap
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun createDefaultAlbumArt(): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            android.graphics.Color.parseColor("#FF416C"),
            android.graphics.Color.parseColor("#FF4B2B"),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 180f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText("🎵", canvas.width / 2f, yPos, textPaint)
        return bitmap
    }

    private fun buildMediaNotification(track: Track, isPlaying: Boolean, artBitmap: Bitmap?): android.app.Notification {
        createNotificationChannel()
        val playPausePendingIntent = android.app.PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlaybackService::class.java).apply { action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val prevPendingIntent = android.app.PendingIntent.getService(
            this, 2,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val nextPendingIntent = android.app.PendingIntent.getService(
            this, 3,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val deletePendingIntent = android.app.PendingIntent.getService(
            this, 4,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_DISMISS },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val openActivityPendingIntent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val finalArt = artBitmap ?: createDefaultAlbumArt()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(openActivityPendingIntent)
            .setOngoing(isPlaying)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLargeIcon(finalArt)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        builder.addAction(R.drawable.ic_prev_vector, "Previous", prevPendingIntent)
        if (isPlaying) {
            builder.addAction(R.drawable.ic_pause_vector, "Pause", playPausePendingIntent)
        } else {
            builder.addAction(R.drawable.ic_play_vector, "Play", playPausePendingIntent)
        }
        builder.addAction(R.drawable.ic_next_vector, "Next", nextPendingIntent)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.playback_control_desc)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildTempNotification(): android.app.Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("").setSilent(true).build()
    }

    private fun stopForegroundService() {
        isPlaying = false
        abandonAudioFocus()
        MusicViewModel.instance?.pausePlayback()
        stopForegroundCompat(true)
        stopSelf()
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        MusicViewModel.instance?.let { vm ->
            vm.saveQueueToPrefs()
            vm.cleanUpAndRelease()
        }
        stopForegroundService()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        val vm = MusicViewModel.instance
        val currentTrack = vm?.uiState?.value?.currentTrack
        val queue = vm?.uiState?.value?.queue ?: emptyList()

        if (currentTrack != null) {
            val description = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId(currentTrack.id.toString()).setTitle(currentTrack.title).setSubtitle(currentTrack.artist).build()
            mediaItems.add(MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
        } else if (queue.isNotEmpty()) {
            val first = queue.first()
            val description = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId(first.id.toString()).setTitle(first.title).setSubtitle(first.artist).build()
            mediaItems.add(MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
        } else {
            val description = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId("placeholder_track").setTitle("Music").setSubtitle("Play your favorite music").build()
            mediaItems.add(MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
        }
        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        abandonAudioFocus()
        try { unregisterReceiver(becomingNoisyReceiver) } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error unregistering becomingNoisyReceiver", e)
        }
        MusicViewModel.instance?.saveQueueToPrefs()
        mediaSession.release()
        activeService = null
        com.mahdi.musicpro.ui.MusicViewModel.updateStrongReference()
        lastUpdateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "music_pro_playback_channel"

        const val ACTION_PLAY = "com.mahdi.musicpro.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mahdi.musicpro.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.mahdi.musicpro.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.mahdi.musicpro.ACTION_NEXT"
        const val ACTION_STOP = "com.mahdi.musicpro.ACTION_STOP"
        const val ACTION_DISMISS = "com.mahdi.musicpro.ACTION_DISMISS"

        @Volatile
        var activeService: MusicPlaybackService? = null

        fun isActive(): Boolean = activeService != null

        fun updateSessionPlaybackStateOnly(context: Context, isPlaying: Boolean, progressMs: Long) {
            val service = activeService ?: return
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    progressMs, 1.0f
                )
            service.mediaSession.setPlaybackState(stateBuilder.build())
        }

        fun updatePlaybackState(context: Context, track: Track?, isPlaying: Boolean, progressMs: Long) {
            activeService?.updatePlayback(track, isPlaying, progressMs)
        }

        fun stopService(context: Context) {
            context.startService(Intent(context, MusicPlaybackService::class.java).apply { action = ACTION_STOP })
        }
    }
}

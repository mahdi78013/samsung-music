package com.mahdi.musicpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import android.widget.RemoteViews
import com.mahdi.musicpro.MainActivity
import com.mahdi.musicpro.MusicPlaybackService
import com.mahdi.musicpro.ui.MusicViewModel
import com.mahdi.musicpro.R

class MusicProWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Initial / fallback update
        val fallbackTitle = context.getString(R.string.now_playing_no_track)
        for (appWidgetId in appWidgetIds) {
            updateWidgetState(context, appWidgetManager, appWidgetId, fallbackTitle, "", false, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_WIDGET_UPDATE) {
            val title = intent.getStringExtra("extra_title") ?: context.getString(R.string.now_playing_no_track)
            val artist = intent.getStringExtra("extra_artist") ?: ""
            val isPlaying = intent.getBooleanExtra("extra_is_playing", false)
            val artUri = intent.getStringExtra("extra_art_uri")

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicProWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            for (appWidgetId in appWidgetIds) {
                updateWidgetState(context, appWidgetManager, appWidgetId, title, artist, isPlaying, artUri)
            }
        } else if (action == ACTION_WIDGET_PLAY || action == ACTION_WIDGET_PAUSE || action == ACTION_WIDGET_NEXT || action == ACTION_WIDGET_PREV) {
            val vm = MusicViewModel.instance
            if (vm != null) {
                when (action) {
                    ACTION_WIDGET_PLAY -> vm.resumePlayback()
                    ACTION_WIDGET_PAUSE -> vm.pausePlayback()
                    ACTION_WIDGET_NEXT -> vm.skipNext()
                    ACTION_WIDGET_PREV -> vm.skipPrevious()
                }
            } else {
                // If the app's ViewModel is not in memory, launch or start the background service securely.
                // If blocked by modern Android API restrictions, fallback gracefully to starting MainActivity with the requested autoplay activity intent.
                val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                    this.action = when (action) {
                        ACTION_WIDGET_PLAY -> MusicPlaybackService.ACTION_PLAY
                        ACTION_WIDGET_PAUSE -> MusicPlaybackService.ACTION_PAUSE
                        ACTION_WIDGET_NEXT -> MusicPlaybackService.ACTION_NEXT
                        ACTION_WIDGET_PREV -> MusicPlaybackService.ACTION_PREVIOUS
                        else -> null
                    }
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    try {
                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (ex: Exception) {
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("extra_widget_action", action)
                        }
                        context.startActivity(mainIntent)
                    }
                }
            }
        }
    }

    private fun updateWidgetState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        artist: String,
        isPlaying: Boolean,
        artUriStr: String?
    ) {
        val nextIntent = Intent(context, MusicProWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, MusicProWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(context, MusicProWidgetProvider::class.java).apply {
            action = if (isPlaying) ACTION_WIDGET_PAUSE else ACTION_WIDGET_PLAY
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_open_player", true)
        }
        val openActivityPendingIntent = PendingIntent.getActivity(
            context,
            204,
            openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("music_pro_prefs", Context.MODE_PRIVATE)
        val widgetStyle = prefs.getString("widget_style", "wide") ?: "wide"
        val isCompact = widgetStyle == "compact"
        val useRoundShape = prefs.getBoolean("widget_use_round", true)
        val transparency = prefs.getFloat("widget_transparency", 0.4f)

        val layoutId = if (isCompact) R.layout.music_widget_layout_compact else R.layout.music_widget_layout_wide
        val views = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_title, title)

            // Click background or text/art to open the player in-app
            setOnClickPendingIntent(R.id.widget_root, openActivityPendingIntent)
            setOnClickPendingIntent(R.id.widget_title, openActivityPendingIntent)
            setOnClickPendingIntent(R.id.widget_play, playPausePendingIntent)

            if (!isCompact) {
                setTextViewText(R.id.widget_artist, artist)
                setOnClickPendingIntent(R.id.widget_album_art, openActivityPendingIntent)
                setOnClickPendingIntent(R.id.widget_artist, openActivityPendingIntent)
                setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
                setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)
            }

            // Dynamic Background Styling
            val bgBitmap = getWidgetBackgroundBitmap(context, transparency, useRoundShape, isCompact)
            setImageViewBitmap(R.id.widget_background_image, bgBitmap)

            // Beautiful vector Play/Pause icon with premium layout instead of ugly system gray
            val playIconRes = if (isPlaying) {
                R.drawable.ic_pause_vector
            } else {
                R.drawable.ic_play_vector
            }
            setImageViewResource(R.id.widget_play, playIconRes)

            // Dynamic Album Art
            if (!isCompact) {
                val bitmap = getAlbumArtBitmap(context, artUriStr)
                if (bitmap != null) {
                    setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getWidgetBackgroundBitmap(context: Context, transparency: Float, useRoundShape: Boolean, isCompact: Boolean): Bitmap {
        val width = 600
        val height = if (isCompact) 120 else 180
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((transparency * 255).toInt(), 22, 24, 36) // #161824
            style = android.graphics.Paint.Style.FILL
        }
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(38, 255, 255, 255) // #26FFFFFF
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }
        val rect = android.graphics.RectF(2f, 2f, width.toFloat() - 2f, height.toFloat() - 2f)
        val radius = if (useRoundShape) 28f else 6f
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        return bitmap
    }

    private fun getAlbumArtBitmap(context: Context, albumArtUriStr: String?): Bitmap? {
        return try {
            val cacheFile = java.io.File(context.cacheDir, "widget_art_cache.jpg")
            if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE = "com.mahdi.musicpro.ACTION_WIDGET_UPDATE"
        const val ACTION_WIDGET_PLAY = "com.mahdi.musicpro.ACTION_WIDGET_PLAY"
        const val ACTION_WIDGET_PAUSE = "com.mahdi.musicpro.ACTION_WIDGET_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.mahdi.musicpro.ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.mahdi.musicpro.ACTION_WIDGET_PREV"

        fun updateWidgetDirectly(context: Context, title: String, artist: String, isPlaying: Boolean, artUriStr: String?) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, MusicProWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                val provider = MusicProWidgetProvider()
                for (appWidgetId in appWidgetIds) {
                    provider.updateWidgetState(context, appWidgetManager, appWidgetId, title, artist, isPlaying, artUriStr)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicProWidgetProvider", "Direct widget update failed", e)
            }
        }
    }
}

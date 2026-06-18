package com.mahdi.musicpro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.mahdi.musicpro.ui.MusicViewModel
import com.mahdi.musicpro.ui.MusicProUi
import com.mahdi.musicpro.ui.theme.MusicProTheme
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener

class MainActivity : AppCompatActivity() {
  companion object {
    private const val ADIVERY_INTERSTITIAL_PLACEMENT_ID = "a1e5b2ca-fddd-4487-a996-add675a772fe"
    private var hasShownAd = false
    private var hasRegisteredListener = false
    private var isAdShowingNow = false
  }

  private val musicViewModel: MusicViewModel by viewModels {
    com.mahdi.musicpro.ui.MusicViewModelFactory(application)
  }

  private val requestNotificationPermissionLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) { _ -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_MusicPro)
    super.onCreate(savedInstanceState)

    // Setup and display Adivery Interstitial Ad on enter (only once per app session/cold start and limit to 1 per 24 hours locally)
    val prefs = getSharedPreferences("music_pro_prefs", MODE_PRIVATE)
    val lastAdTime = prefs.getLong("last_ad_shown_timestamp", 0L)
    val currentTime = System.currentTimeMillis()
    val isAllowedToShow = (currentTime - lastAdTime) >= 24 * 60 * 60 * 1000L // 24 hours

    if (!hasShownAd && isAllowedToShow) {
      try {
        if (!hasRegisteredListener) {
          hasRegisteredListener = true
          Adivery.addGlobalListener(object : AdiveryListener() {
            override fun onInterstitialAdLoaded(placementId: String) {
              if (placementId == ADIVERY_INTERSTITIAL_PLACEMENT_ID) {
                if (!hasShownAd && !isAdShowingNow) {
                  isAdShowingNow = true
                  hasShownAd = true
                  prefs.edit().putLong("last_ad_shown_timestamp", System.currentTimeMillis()).apply()
                  Adivery.showAd(placementId)
                }
              }
            }

            override fun onInterstitialAdClosed(placementId: String) {
              if (placementId == ADIVERY_INTERSTITIAL_PLACEMENT_ID) {
                isAdShowingNow = false
                // Preload the next ad in the background, but do not automatically show it
                val shouldPreload = (System.currentTimeMillis() - prefs.getLong("last_ad_shown_timestamp", 0L)) >= 24 * 60 * 60 * 1000L
                if (shouldPreload) {
                  Adivery.prepareInterstitialAd(this@MainActivity, ADIVERY_INTERSTITIAL_PLACEMENT_ID)
                }
              }
            }
          })
        }
        if (!isAdShowingNow) {
          Adivery.prepareInterstitialAd(this, ADIVERY_INTERSTITIAL_PLACEMENT_ID)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    
    // Check if launched from Widget fallback
    intent?.getStringExtra("extra_widget_action")?.let { action ->
      when (action) {
        "com.mahdi.musicpro.ACTION_WIDGET_PLAY" -> musicViewModel.resumePlayback()
        "com.mahdi.musicpro.ACTION_WIDGET_PAUSE" -> musicViewModel.pausePlayback()
        "com.mahdi.musicpro.ACTION_WIDGET_NEXT" -> musicViewModel.skipNext()
        "com.mahdi.musicpro.ACTION_WIDGET_PREV" -> musicViewModel.skipPrevious()
      }
    }
    if (intent?.getBooleanExtra("extra_open_player", false) == true) {
      musicViewModel.triggerPlayerExpansion()
    }

    // Request notification permission on Android 13+ (SDK 33)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (androidx.core.content.ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    enableEdgeToEdge()
    volumeControlStream = android.media.AudioManager.STREAM_MUSIC
    setContent {
      MusicProTheme(darkTheme = true) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MusicProUi(
              viewModel = musicViewModel,
              innerPadding = innerPadding
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      androidx.core.content.ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.READ_MEDIA_AUDIO
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
      androidx.core.content.ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (hasPermission && !musicViewModel.hasLoadedOnce) {
      musicViewModel.loadTracksFromDevice()
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra("extra_widget_action")?.let { action ->
      when (action) {
        "com.mahdi.musicpro.ACTION_WIDGET_PLAY" -> musicViewModel.resumePlayback()
        "com.mahdi.musicpro.ACTION_WIDGET_PAUSE" -> musicViewModel.pausePlayback()
        "com.mahdi.musicpro.ACTION_WIDGET_NEXT" -> musicViewModel.skipNext()
        "com.mahdi.musicpro.ACTION_WIDGET_PREV" -> musicViewModel.skipPrevious()
      }
    }
    if (intent.getBooleanExtra("extra_open_player", false)) {
      musicViewModel.triggerPlayerExpansion()
    }
  }
}

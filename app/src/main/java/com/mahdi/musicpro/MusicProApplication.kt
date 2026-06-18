package com.mahdi.musicpro

import android.app.Application
import com.adivery.sdk.Adivery

class MusicProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Adivery SDK with the user's App ID
        try {
            Adivery.configure(this, "2c8789f6-8aac-47ad-b124-2ef4fff73704")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

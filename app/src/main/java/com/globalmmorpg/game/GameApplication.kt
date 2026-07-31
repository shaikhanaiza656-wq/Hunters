package com.globalmmorpg.game

import android.app.Application
import com.google.firebase.FirebaseApp

class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Requires a REAL google-services.json downloaded from your Firebase project
        // (Firebase Console -> Project Settings -> Your apps -> google-services.json)
        FirebaseApp.initializeApp(this)
    }
}

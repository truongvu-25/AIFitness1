package com.google.mediapipe.examples.poselandmarker.config

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseConfig {
    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
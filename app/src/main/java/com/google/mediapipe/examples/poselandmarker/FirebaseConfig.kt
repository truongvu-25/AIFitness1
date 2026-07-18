package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseConfig {
    const val API_KEY = "AIzaSyCrXLTyE-HCuSUD8abogB77KnCrypMHSIE"
    const val PROJECT_ID = "aifitness1"
    const val APPLICATION_ID = "1:628615666069:android:c12f842a7ae97e03497e9c"
    const val GCM_SENDER_ID = "628615666069"

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APPLICATION_ID)
                    .setProjectId(PROJECT_ID)
                    .setGcmSenderId(GCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

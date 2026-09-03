package com.google.mediapipe.examples.poselandmarker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NotificationHelper.scheduleDailyReminder(context)
        }
    }
}

package com.google.mediapipe.examples.poselandmarker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.mediapipe.examples.poselandmarker.receiver.WorkoutReminderReceiver
import java.util.Calendar

object NotificationHelper {
    private const val ALARM_REQ_CODE = 1001
    private const val PREFERENCES_NAME = "tri_force_reminder"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val DEFAULT_HOUR = 8
    private const val DEFAULT_MINUTE = 0

    fun scheduleDailyReminder(context: Context) {
        try {
            if (!isReminderEnabled(context)) {
                cancelReminder(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WorkoutReminderReceiver::class.java)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, getReminderHour(context))
                set(Calendar.MINUTE, getReminderMinute(context))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isReminderEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleDailyReminder(context) else cancelReminder(context)
    }

    fun getReminderHour(context: Context): Int {
        return preferences(context).getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)
    }

    fun getReminderMinute(context: Context): Int {
        return preferences(context).getInt(KEY_MINUTE, DEFAULT_MINUTE).coerceIn(0, 59)
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        preferences(context)
            .edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        if (isReminderEnabled(context)) scheduleDailyReminder(context)
    }

    fun cancelReminder(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WorkoutReminderReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

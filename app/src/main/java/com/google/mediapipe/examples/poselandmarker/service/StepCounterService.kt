package com.google.mediapipe.examples.poselandmarker.service

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.poselandmarker.model.DailyStepCounter
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mediapipe.examples.poselandmarker.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STEPS_UPDATED = "com.google.mediapipe.examples.poselandmarker.STEPS_UPDATED"
        const val EXTRA_STEPS = "extra_steps"
        const val EXTRA_CALORIES = "extra_calories"

        private const val PREFS_NAME = "step_counter_prefs"
        private const val PREF_KEY_STEPS_PREFIX = "steps_"

        fun getTodayKey(): String {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
            return sdf.format(Date())
        }

        fun getSavedSteps(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_KEY_STEPS_PREFIX + getTodayKey(), 0)
        }

        fun getSavedCalories(context: Context): Float {
            return getSavedSteps(context) * 0.04f
        }

        fun startService(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
                val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                if (manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) return
                val intent = Intent(context, StepCounterService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val binder = StepBinder()
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var currentStepsCount: Int = 0
    private lateinit var dailyCounter: DailyStepCounter
    private var lastNotificationAt = 0L

    inner class StepBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentStepsCount = prefs.getInt(PREF_KEY_STEPS_PREFIX + getTodayKey(), 0)
        val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val sameBoot = prefs.getInt("boot_count", -2) == bootCount
        val previousRaw = if (sameBoot) prefs.getInt("last_raw_" + getTodayKey(), -1) else -1
        dailyCounter = DailyStepCounter(getTodayKey(), currentStepsCount, previousRaw)
        prefs.edit().putInt("boot_count", bootCount).apply()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(currentStepsCount, currentStepsCount * 0.04f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (stepSensor == null ||
                !sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val rawSensorSteps = event.values[0].toInt()

            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val todayKey = getTodayKey()

            currentStepsCount = dailyCounter.record(todayKey, rawSensorSteps)
            prefs.edit()
                .putInt(PREF_KEY_STEPS_PREFIX + todayKey, currentStepsCount)
                .putInt("last_raw_" + todayKey, rawSensorSteps)
                .apply()

            val calories = currentStepsCount * 0.04f
            updateNotification(currentStepsCount, calories)
            broadcastStepsUpdate(currentStepsCount, calories)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }

    private fun broadcastStepsUpdate(steps: Int, calories: Float) {
        val intent = Intent(ACTION_STEPS_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_STEPS, steps)
            putExtra(EXTRA_CALORIES, calories)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(steps: Int, calories: Float): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val caloStr = String.format(Locale.US, "%.1f", calories)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TRI FORCE - Đang đếm bước")
            .setContentText("Đã đi: $steps bước (~$caloStr kcal tiêu thụ)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(steps: Int, calories: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationAt < 5_000L) return
        lastNotificationAt = now
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(steps, calories))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Đếm bước chân ngầm"
            val descriptionText = "Hiển thị đếm số bước chân và calo tiêu thụ ngầm real-time"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (stepSensor != null) {
            sensorManager.unregisterListener(this)
        }
    }
}

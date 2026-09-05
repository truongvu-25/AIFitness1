package com.google.mediapipe.examples.poselandmarker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.poselandmarker.MainActivity
import com.google.mediapipe.examples.poselandmarker.R
import java.text.NumberFormat
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
        private const val PREF_KEY_INITIAL_STEPS_PREFIX = "initial_steps_"

        fun getTodayKey(): String {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getSavedSteps(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_KEY_STEPS_PREFIX + getTodayKey(), 0)
        }

        fun getSavedCalories(context: Context): Float {
            return getSavedSteps(context) * 0.04f
        }

        fun hasActivityRecognitionPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED

        fun startService(context: Context) {
            if (!hasActivityRecognitionPermission(context)) return
            try {
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

        fun stopService(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }

    private val binder = StepBinder()
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var currentStepsCount: Int = 0
    private var initialSensorSteps: Int = -1

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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor != null && hasActivityRecognitionPermission(this)) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(currentStepsCount, currentStepsCount * 0.04f)
        if (!hasActivityRecognitionPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val rawSensorSteps = event.values[0].toInt()

            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val todayKey = getTodayKey()

            if (initialSensorSteps < 0) {
                initialSensorSteps = prefs.getInt(PREF_KEY_INITIAL_STEPS_PREFIX + todayKey, -1)
                if (initialSensorSteps < 0) {
                    initialSensorSteps = rawSensorSteps
                    prefs.edit().putInt(PREF_KEY_INITIAL_STEPS_PREFIX + todayKey, initialSensorSteps).apply()
                }
            }

            val stepsToday = (rawSensorSteps - initialSensorSteps).coerceAtLeast(0)
            currentStepsCount = stepsToday

            prefs.edit().putInt(PREF_KEY_STEPS_PREFIX + todayKey, currentStepsCount).apply()

            val calories = currentStepsCount * 0.04f
            updateNotification(currentStepsCount, calories)
            broadcastStepsUpdate(currentStepsCount, calories)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }

    fun addSimulatedSteps(amount: Int = 50) {
        currentStepsCount += amount
        val todayKey = getTodayKey()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putInt(PREF_KEY_STEPS_PREFIX + todayKey, currentStepsCount).apply()

        val calories = currentStepsCount * 0.04f
        updateNotification(currentStepsCount, calories)
        broadcastStepsUpdate(currentStepsCount, calories)
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caloStr = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
        }.format(calories)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_steps_title))
            .setContentText(getString(R.string.notification_steps_text, steps, caloStr))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(steps: Int, calories: Float) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(steps, calories))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_steps_channel)
            val descriptionText = getString(R.string.notification_steps_channel_description)
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

package com.google.mediapipe.examples.poselandmarker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.Locale

class RestTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIFICATION_ID = 4001
        const val REST_DURATION_MS = 5 * 60 * 1000L // 5 minutes (300 seconds)

        const val ACTION_START_REST = "com.google.mediapipe.examples.poselandmarker.START_REST"
        const val ACTION_STOP_REST = "com.google.mediapipe.examples.poselandmarker.STOP_REST"
        const val EXTRA_DAY_INDEX = "extra_day_index"

        fun startRestTimer(context: Context, dayIndex: Int) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START_REST
                putExtra(EXTRA_DAY_INDEX, dayIndex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP_REST
            }
            context.stopService(intent)
        }
    }

    private var countDownTimer: CountDownTimer? = null
    private var dayIndex: Int = 1
    private var secondsRemaining: Int = 300

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_REST) {
            stopRestTimer()
            stopSelf()
            return START_NOT_STICKY
        }

        dayIndex = intent?.getIntExtra(EXTRA_DAY_INDEX, 1) ?: 1

        val initialNotification = buildOngoingNotification("05:00")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startRestCountDown()
        return START_STICKY
    }

    private fun startRestCountDown() {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(REST_DURATION_MS, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = (millisUntilFinished / 1000L).toInt()
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                val timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                updateOngoingNotification(timeStr)
            }

            override fun onFinish() {
                showRestExpiredNotification()
                stopSelf()
            }
        }.start()
    }

    private fun stopRestTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildOngoingNotification(timeStr: String): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Thời gian nghỉ giữa các bài tập ($timeStr)")
            .setContentText("Hãy thả lỏng cơ bắp. Bấm vào đây để sẵn sàng cho bài tập tiếp theo!")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateOngoingNotification(timeStr: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification(timeStr))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showRestExpiredNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expiredNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Đã hết 5 phút nghỉ ngơi! 🔔")
            .setContentText("Thời gian nghỉ đã hết. Hãy quay lại tập luyện bài tiếp theo trong Ngày $dayIndex ngay nhé!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, expiredNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Đếm giờ nghỉ giữa bài tập"
            val descriptionText = "Đếm ngược 5 phút nghỉ ngơi và nhắc nhở bài tập tiếp theo"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

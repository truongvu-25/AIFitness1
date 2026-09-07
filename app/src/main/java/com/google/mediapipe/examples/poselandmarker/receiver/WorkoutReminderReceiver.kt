package com.google.mediapipe.examples.poselandmarker.receiver

<<<<<<< HEAD
=======
import android.Manifest
>>>>>>> huy2
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
<<<<<<< HEAD
=======
import android.content.pm.PackageManager
import android.os.Build
>>>>>>> huy2
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.FitnessApplication
import com.google.mediapipe.examples.poselandmarker.MainActivity
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay

class WorkoutReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationHelper.isReminderEnabled(context)) {
            NotificationHelper.cancelReminder(context)
            return
        }

        // Reschedule alarm for the next day
        NotificationHelper.scheduleDailyReminder(context)

        val pendingResult = goAsync()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val profile = document.toObject(UserProfile::class.java)
                        if (profile != null) {
                            val createdTime = profile.createdTime
                            val diffMs = System.currentTimeMillis() - createdTime
                            val dayIndex = (diffMs / (24 * 60 * 60 * 1000)).toInt() + 1

                            if (dayIndex in 1..30) {
                                // Fetch workout list for the calculated dayIndex
                                db.collection("users").document(currentUser.uid)
                                    .collection("workouts").document("day_$dayIndex").get()
                                    .addOnSuccessListener { workoutDoc ->
                                        if (workoutDoc.exists()) {
                                            val workoutDay = workoutDoc.toObject(WorkoutDay::class.java)
                                            val pendingCount = workoutDay?.exercises
                                                ?.count { it.status == 0 } ?: 0
                                            if (pendingCount > 0) {
                                                showNotification(context, dayIndex, pendingCount)
                                            }
                                        }
                                        pendingResult.finish()
                                    }
                                    .addOnFailureListener {
                                        pendingResult.finish()
                                    }
                            } else {
                                pendingResult.finish()
                            }
                        } else {
                            pendingResult.finish()
                        }
                    } else {
                        pendingResult.finish()
                    }
                }
                .addOnFailureListener {
                    pendingResult.finish()
                }
        } else {
            pendingResult.finish()
        }
    }

    private fun showNotification(context: Context, dayIndex: Int, pendingCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open MainActivity when clicking notification
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, flags)

        val notification = NotificationCompat.Builder(context, FitnessApplication.Companion.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outline)
            .setContentTitle(context.getString(R.string.notification_workout_title, dayIndex))
            .setContentText(context.getString(R.string.notification_workout_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}

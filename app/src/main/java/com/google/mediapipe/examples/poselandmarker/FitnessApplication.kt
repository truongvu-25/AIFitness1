package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.config.FirebaseConfig
import com.google.mediapipe.examples.poselandmarker.model.ExerciseDetails

class FitnessApplication : Application() {

    companion object {
        const val CHANNEL_ID = "workout_reminder_channel"
        private const val TAG = "FitnessApplication"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseConfig.initialize(this)
            initializeExerciseDatabase()
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Nhắc nhở tập luyện"
                val descriptionText = "Nhắc nhở bài tập hàng ngày lúc 8:00 sáng"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel", e)
        }
    }

    private fun initializeExerciseDatabase() {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // Master static exercise details
            val exercises = listOf(
                ExerciseDetails(
                    id = "pushup",
                    name = "Hít Đất (Push-up)",
                    description = "Giữ thẳng lưng, hạ ngực sát sàn rồi đẩy lên.",
                    videoUrl = "asset:///videos/HitDatTriForce.mp4",
                    isTimed = false,
                    unit = "lần"
                ),
                ExerciseDetails(
                    id = "squat",
                    name = "Ngồi Xổm (Squat)",
                    description = "Gập gối hạ hông xuống sâu, giữ lưng thẳng.",
                    videoUrl = "asset:///videos/squat.mp4",
                    isTimed = false,
                    unit = "lần"
                ),
                ExerciseDetails(
                    id = "jumpingjack",
                    name = "Nhảy Dang Tay Chân (Jumping Jack)",
                    description = "Bật nhảy dang rộng chân đồng thời vung hai tay chạm nhau ở trên đầu.",
                    videoUrl = "asset:///videos/jumping_jack.mp4",
                    isTimed = false,
                    unit = "lần"
                ),
                ExerciseDetails(
                    id = "situp",
                    name = "Gập Bụng (Sit-up)",
                    description = "Nằm ngửa gối co, dùng cơ bụng kéo thân trên ngồi dậy hoàn toàn.",
                    videoUrl = "asset:///videos/GapBungTriForce.mp4",
                    isTimed = false,
                    unit = "lần"
                ),
                ExerciseDetails(
                    id = "plank",
                    name = "Giữ Thân (Plank)",
                    description = "Tì khuỷu tay xuống sàn, giữ thẳng toàn thân song song với sàn.",
                    videoUrl = "asset:///videos/plank.mp4",
                    isTimed = true,
                    unit = "giây"
                ),
                ExerciseDetails(
                    id = "sideplank",
                    name = "Plank Nghiêng (Side Plank)",
                    description = "Nằm nghiêng, tì một khuỷu tay nâng hông lên cao giữ cơ thể thẳng.",
                    videoUrl = "asset:///videos/side_plank.mp4",
                    isTimed = true,
                    unit = "giây"
                ),
                ExerciseDetails(
                    id = "splitsquat",
                    name = "Ngồi Xổm Một Chân (Split Squat)",
                    description = "Đứng chân trước chân sau rộng, hạ đầu gối chân sau xuống vuông góc.",
                    videoUrl = "asset:///videos/split_squat.mp4",
                    isTimed = false,
                    unit = "lần"
                )
            )

            val batch = db.batch()
            for (exercise in exercises) {
                val docRef = db.collection("exercises").document(exercise.id)
                batch.set(docRef, exercise)
            }
            batch.commit()
                .addOnFailureListener { e ->
                    Log.w(TAG, "Firestore exercise database sync warning: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to initialize remote exercise database: ${e.message}")
        }
    }
}

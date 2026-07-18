package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore

class FitnessApplication : Application() {

    companion object {
        const val CHANNEL_ID = "workout_reminder_channel"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.initialize(this)
        initializeExerciseDatabase()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nhắc nhở tập luyện"
            val descriptionText = "Nhắc nhở bài tập hàng ngày lúc 8:00 sáng"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeExerciseDatabase() {
        val db = FirebaseFirestore.getInstance()
        
        // Master static exercise details (lightweight w3schools videos)
        val exercises = listOf(
            ExerciseDetails(
                id = "pushup",
                name = "Hít Đất (Push-up)",
                description = "Giữ thẳng lưng, hạ ngực sát sàn rồi đẩy lên.",
                videoUrl = "https://raw.githubusercontent.com/samarthify/AI-Fitness-Trainer/master/pushup.mp4"
            ),
            ExerciseDetails(
                id = "squat",
                name = "Ngồi Xổm (Squat)",
                description = "Gập gối hạ hông xuống sâu, giữ lưng thẳng.",
                videoUrl = "https://raw.githubusercontent.com/samarthify/AI-Fitness-Trainer/master/squats.mp4"
            ),
            ExerciseDetails(
                id = "jumpingjack",
                name = "Nhảy Dang Tay Chân (Jumping Jack)",
                description = "Bật nhảy dang rộng chân đồng thời vung hai tay chạm nhau ở trên đầu.",
                videoUrl = "https://raw.githubusercontent.com/PegHeads-Inc/PegHeads-Tutorial-1/main/jumpingjack.mp4"
            ),
            ExerciseDetails(
                id = "situp",
                name = "Gập Bụng (Sit-up)",
                description = "Nằm ngửa gối co, dùng cơ bụng kéo thân trên ngồi dậy hoàn toàn.",
                videoUrl = "https://raw.githubusercontent.com/JasonYapzx/sportform/main/situp.mp4"
            ),
            ExerciseDetails(
                id = "plank",
                name = "Giữ Thân (Plank)",
                description = "Tì khuỷu tay xuống sàn, giữ thẳng toàn thân song song với sàn.",
                videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4"
            ),
            ExerciseDetails(
                id = "sideplank",
                name = "Plank Nghiêng (Side Plank)",
                description = "Nằm nghiêng, tì một khuỷu tay nâng hông lên cao giữ cơ thể thẳng.",
                videoUrl = "https://www.w3schools.com/html/movie.mp4"
            ),
            ExerciseDetails(
                id = "splitsquat",
                name = "Ngồi Xổm Một Chân (Split Squat)",
                description = "Đứng chân trước chân sau rộng, hạ đầu gối chân sau xuống vuông góc.",
                videoUrl = "https://www.w3schools.com/html/movie.mp4"
            )
        )

        // Write batch to Firestore directly to ensure exercises are always up to date
        val batch = db.batch()
        for (exercise in exercises) {
            val docRef = db.collection("exercises").document(exercise.id)
            batch.set(docRef, exercise)
        }
        batch.commit()
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }
}

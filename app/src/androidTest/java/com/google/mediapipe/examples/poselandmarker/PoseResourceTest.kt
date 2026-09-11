package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.poselandmarker.analysis.PoseLandmarkerHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PoseResourceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun asyncInputIsReleasedAfterResultAndShutdown() {
        val received = CountDownLatch(1)
        var failure: String? = null
        val helper = PoseLandmarkerHelper(context = context, runningMode = RunningMode.LIVE_STREAM,
            currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE,
            poseLandmarkerHelperListener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String, errorCode: Int) { failure = error; received.countDown() }
                override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) { received.countDown() }
            })
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            val image = BitmapImageBuilder(bitmap).build()
            helper.detectAsync(image, 1L)
            assertTrue("No inference callback", received.await(15, TimeUnit.SECONDS))
            assertNull(failure)
        } finally { helper.clearPoseLandmarker() }
        assertTrue("Input bitmap was retained after shutdown", bitmap.isRecycled)
    }

    @Test fun corruptVideoReturnsFailureAndReleasesRetriever() {
        val file = File(context.cacheDir, "invalid-test-video.mp4").apply { writeText("invalid") }
        val helper = PoseLandmarkerHelper(context = context, runningMode = RunningMode.VIDEO,
            currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE)
        try {
            repeat(3) { assertNull(helper.detectVideoFile(Uri.fromFile(file), 300L)) }
        } finally { helper.clearPoseLandmarker(); file.delete() }
    }
}

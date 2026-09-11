/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicReference

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Pose Landmarker will not change, a lazy val would be preferable.
    private var poseLandmarker: PoseLandmarker? = null
    private var bitmapBuffer: Bitmap? = null
    private val pendingImage = AtomicReference<MPImage?>(null)

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
        pendingImage.getAndSet(null)?.close()
        bitmapBuffer?.recycle()
        bitmapBuffer = null
    }

    // Return running status of PoseLandmarkerHelper
    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    // Initialize the Pose landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupPoseLandmarker() {
        // Set general pose landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
                MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
                MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
                else -> "pose_landmarker_full.task"
            }

        baseOptionBuilder.setModelAssetPath(modelName)

        // Check if runningMode is consistent with poseLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Pose Landmarker.
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to PoselandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        try {
            // Keep only one immutable input alive until native inference has finished.
            if (pendingImage.get() != null || poseLandmarker == null) return
            val frameTime = SystemClock.uptimeMillis()
            val imageWidth = imageProxy.width
            val imageHeight = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Reuse the RGBA bitmap between frames to avoid continuous large allocations and GC.
            val frameBuffer = bitmapBuffer
                ?.takeIf { !it.isRecycled && it.width == imageWidth && it.height == imageHeight }
                ?: Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888).also {
                    bitmapBuffer = it
                }

            val pixelBuffer = imageProxy.planes[0].buffer
            pixelBuffer.rewind()
            frameBuffer.copyPixelsFromBuffer(pixelBuffer)

            val matrix = Matrix().apply {
                // Rotate the frame received from the camera to be in the same direction as it'll be shown
                postRotate(rotationDegrees.toFloat())

                // flip image if user use front camera
                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageWidth.toFloat(),
                        imageHeight.toFloat()
                    )
                }
            }
            val transformedBitmap = Bitmap.createBitmap(
                frameBuffer, 0, 0, frameBuffer.width, frameBuffer.height,
                matrix, true
            )
            // createBitmap may return its source when the transform is effectively identity.
            // The async landmarker must own an immutable frame while frameBuffer is reused.
            val rotatedBitmap = if (transformedBitmap === frameBuffer) {
                frameBuffer.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                transformedBitmap
            }

            // Convert the input Bitmap object to an MPImage object to run inference
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            detectAsync(mpImage, frameTime)
        } finally {
            imageProxy.close()
        }
    }

    // Run pose landmark using MediaPipe Pose Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        // Takes ownership: early close can recycle a bitmap while native code still reads it.
        val landmarker = poseLandmarker
        if (landmarker == null || !pendingImage.compareAndSet(null, mpImage)) {
            mpImage.close()
            return
        }
        try {
            landmarker.detectAsync(mpImage, frameTime)
        } catch (error: RuntimeException) {
            pendingImage.getAndSet(null)?.close()
            throw error
        }
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // pose landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        require(inferenceIntervalMs > 0) { "Frame interval must be positive" }
        val startTime = SystemClock.uptimeMillis()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val results = mutableListOf<PoseLandmarkerResult>()
            var width = 0
            var height = 0
            var timestampMs = 0L
            while (timestampMs < duration) {
                if (Thread.currentThread().isInterrupted) return null
                val frame = retriever.getFrameAtTime(
                    timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: return null
                val bitmap = if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                width = bitmap.width
                height = bitmap.height
                try {
                    val image = BitmapImageBuilder(bitmap).build()
                    try {
                        val result = poseLandmarker?.detectForVideo(image, timestampMs) ?: return null
                        results.add(result)
                    } finally {
                        image.close()
                    }
                } finally {
                    if (bitmap !== frame) bitmap.recycle()
                    frame.recycle()
                }
                timestampMs += inferenceIntervalMs
            }
            if (results.isEmpty()) return null
            return ResultBundle(results, (SystemClock.uptimeMillis() - startTime) / results.size, height, width)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to analyze video", error)
            return null
        } finally {
            retriever.release()
        }
    }

    // Accepted a Bitmap and runs pose landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image.copy(Bitmap.Config.ARGB_8888, false)).build()

        // Run pose landmarker using MediaPipe Pose Landmarker API
        try {
            poseLandmarker?.detect(mpImage)?.also { landmarkResult ->
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                return ResultBundle(
                    listOf(landmarkResult),
                    inferenceTimeMs,
                    image.height,
                    image.width
                )
            }

            // If poseLandmarker?.detect() returns null, this is likely an error. Returning null
            // to indicate this.
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to detect."
            )
            return null
        } finally {
            mpImage.close()
        }
    }

    // Return the landmark result to this PoseLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        try {
            val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()
            poseLandmarkerHelperListener?.onResults(
                ResultBundle(listOf(result), inferenceTime, input.height, input.width)
            )
        } finally {
            val submitted = pendingImage.getAndSet(null)
            if (submitted !== input) input.close()
            submitted?.close()
        }
    }

    // Return errors thrown during detection to this PoseLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        try {
            poseLandmarkerHelperListener?.onError(
                error.message ?: "An unknown error has occurred"
            )
        } finally {
            pendingImage.getAndSet(null)?.close()
        }
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}

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
package com.google.mediapipe.examples.poselandmarker.ui.fragment.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.examples.poselandmarker.viewmodel.MainViewModel
import com.google.mediapipe.examples.poselandmarker.analysis.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class GalleryFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private val viewModel: MainViewModel by activityViewModels()

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val resultHandler = Handler(Looper.getMainLooper())
    private var generation = 0

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            if (_fragmentGalleryBinding == null) return@registerForActivityResult
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()
        initBottomSheetControls()
    }

    override fun onPause() {
        playbackHandler.removeCallbacksAndMessages(null)
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        super.onPause()
    }

    override fun onDestroyView() {
        generation++
        playbackHandler.removeCallbacksAndMessages(null)
        _fragmentGalleryBinding?.videoView?.stopPlayback()
        _fragmentGalleryBinding?.imageResult?.setImageDrawable(null)
        backgroundExecutor.shutdownNow()
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )

        // When clicked, lower detection score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence >= 0.2) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence <= 0.8) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, lower pose tracking score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence >= 0.2) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise pose tracking score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence <= 0.8) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, lower pose presence score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence >= 0.2) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise pose presence score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence <= 0.8) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {

                    viewModel.setDelegate(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel,
            false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {
                    viewModel.setModel(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        playbackHandler.removeCallbacksAndMessages(null)
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.overlay.clear()
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )

        fragmentGalleryBinding.overlay.clear()
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE
    }

    private fun runDetectionOnImage(uri: Uri) = runDetection(uri, MediaType.IMAGE)

    private fun runDetectionOnVideo(uri: Uri) = runDetection(uri, MediaType.VIDEO)

    private fun runDetection(uri: Uri, type: MediaType) {
        val currentBinding = _fragmentGalleryBinding ?: return
        val appContext = requireContext().applicationContext
        val requestGeneration = ++generation
        playbackHandler.removeCallbacksAndMessages(null)
        currentBinding.videoView.stopPlayback()
        currentBinding.overlay.clear()
        setUiEnabled(false)
        updateDisplayView(type)
        currentBinding.progress.visibility = View.VISIBLE
        val model = viewModel.currentModel
        val delegate = viewModel.currentDelegate
        val detection = viewModel.currentMinPoseDetectionConfidence
        val tracking = viewModel.currentMinPoseTrackingConfidence
        val presence = viewModel.currentMinPosePresenceConfidence
        backgroundExecutor.execute {
            var helper: PoseLandmarkerHelper? = null
            try {
                helper = PoseLandmarkerHelper(
                    context = appContext,
                    runningMode = if (type == MediaType.IMAGE) RunningMode.IMAGE else RunningMode.VIDEO,
                    currentModel = model, currentDelegate = delegate,
                    minPoseDetectionConfidence = detection,
                    minPoseTrackingConfidence = tracking,
                    minPosePresenceConfidence = presence
                )
                if (type == MediaType.IMAGE) {
                    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) {
                                decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val longest = maxOf(info.size.width, info.size.height)
                            if (longest > 2048) decoder.setTargetSampleSize((longest + 2047) / 2048)
                        }
                    } else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        appContext.contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                        require(options.outWidth > 0 && options.outHeight > 0) { "Invalid image" }
                        options.inSampleSize = 1
                        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
                            options.inSampleSize *= 2
                        }
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        appContext.contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it, null, options)
                        } ?: error("Invalid image")
                    }
                    val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
                        try { decoded.copy(Bitmap.Config.ARGB_8888, false) }
                        finally { decoded.recycle() }
                    }
                    // Inference owns a separate bitmap; MPImage.close releases its storage.
                    val result = try {
                        helper.detectImage(bitmap) ?: error("Không thể phân tích ảnh này.")
                    } catch (error: Exception) {
                        bitmap.recycle()
                        throw error
                    }
                    resultHandler.post {
                        if (_fragmentGalleryBinding !== currentBinding || generation != requestGeneration) {
                            bitmap.recycle()
                            return@post
                        }
                        currentBinding.imageResult.setImageBitmap(bitmap)
                        currentBinding.overlay.setResults(result.results.first(), result.inputImageHeight,
                            result.inputImageWidth, RunningMode.IMAGE)
                        currentBinding.progress.visibility = View.GONE
                        currentBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format(Locale.US, "%d ms", result.inferenceTime)
                        setUiEnabled(true)
                    }
                } else {
                    val result = helper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                        ?: error("Không thể phân tích video này.")
                    resultHandler.post {
                        if (_fragmentGalleryBinding !== currentBinding || generation != requestGeneration) return@post
                        currentBinding.progress.visibility = View.GONE
                        currentBinding.videoView.setOnPreparedListener { player ->
                            player.setVolume(0f, 0f)
                            if (!isResumed) return@setOnPreparedListener
                            currentBinding.videoView.start()
                            displayVideoResult(result, requestGeneration)
                        }
                        currentBinding.videoView.setOnErrorListener { _, _, _ ->
                            classifyingError()
                            true
                        }
                        currentBinding.videoView.setVideoURI(uri)
                        setUiEnabled(true)
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Media analysis failed", error)
                resultHandler.post {
                    if (_fragmentGalleryBinding !== currentBinding || generation != requestGeneration) return@post
                    classifyingError()
                    Toast.makeText(appContext, "Không thể đọc hoặc phân tích tệp. Hãy chọn tệp khác.", Toast.LENGTH_LONG).show()
                }
            } finally {
                helper?.clearPoseLandmarker()
            }
        }
    }

    private fun displayVideoResult(result: PoseLandmarkerHelper.ResultBundle, requestGeneration: Int) {
        val currentBinding = _fragmentGalleryBinding ?: return
        val drawFrame = object : Runnable {
            override fun run() {
                if (_fragmentGalleryBinding !== currentBinding || generation != requestGeneration || !isResumed) return
                val index = (currentBinding.videoView.currentPosition / VIDEO_INTERVAL_MS).toInt()
                result.results.getOrNull(index)?.let {
                    currentBinding.overlay.setResults(it, result.inputImageHeight, result.inputImageWidth, RunningMode.VIDEO)
                }
                if (currentBinding.videoView.isPlaying) playbackHandler.postDelayed(this, VIDEO_INTERVAL_MS)
            }
        }
        currentBinding.bottomSheetLayout.inferenceTimeVal.text = String.format(Locale.US, "%d ms", result.inferenceTime)
        playbackHandler.post(drawFrame)
    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
    }

    private fun classifyingError() {
        activity?.runOnUiThread {
            if (_fragmentGalleryBinding == null || !isAdded) return@runOnUiThread
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        classifyingError()
        activity?.runOnUiThread {
            if (_fragmentGalleryBinding == null || !isAdded) return@runOnUiThread
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // no-op
    }

    companion object {
        private const val TAG = "GalleryFragment"

        // Value used to get frames at specific intervals for inference (e.g. every 300ms)
        private const val VIDEO_INTERVAL_MS = 300L
    }
}

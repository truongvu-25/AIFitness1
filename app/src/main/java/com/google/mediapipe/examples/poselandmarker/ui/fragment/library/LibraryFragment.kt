package com.google.mediapipe.examples.poselandmarker.ui.fragment.library

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentLibraryBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemLibraryExerciseBinding
import java.text.Normalizer
import java.util.Locale

data class LibraryExercise(
    val id: String,
    val name: String,
    val target: String,
    val desc: String,
    val category: String,
    val equipment: String,
    val targetCount: Int,
    val videoUrl: String = "",
    val isCustom: Boolean = false
)

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private var isEquipmentMode = false
    private var isGymMode = false
    private var searchQuery = ""

    private lateinit var adapter: LibraryExerciseAdapter
    private val allExercises = mutableListOf<LibraryExercise>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initExerciseDatabase()
        setupRecyclerView()
        setupSegmentedControls()
        setupSearch()
        setupCustomExerciseButton()
        updateFilter()
    }

    /** Keep the original seven exercise IDs, targets, categories, and asset mappings. */
    private fun initExerciseDatabase() {
        allExercises.clear()
        allExercises.add(
            LibraryExercise(
                "pushup", "Hít Đất (Push-up)", "Mục tiêu: 15 lần",
                "Phát triển cơ ngực, vai và bắp tay sau toàn diện.",
                "Thân trên & Core", "Bodyweight", 15,
                videoUrl = "asset:///videos/push_up.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "situp", "Gập Bụng (Sit-up)", "Mục tiêu: 20 lần",
                "Tăng cường sức mạnh nhóm cơ bụng và core cốt lõi.",
                "Thân trên & Core", "Bodyweight", 20,
                videoUrl = "asset:///videos/sit_up.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "squat", "Ngồi Xổm (Squats)", "Mục tiêu: 20 lần",
                "Xây dựng cơ đùi trước, đùi sau và cơ mông săn chắc.",
                "Thân dưới & Cardio", "Bodyweight", 20,
                videoUrl = "asset:///videos/squat.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "plank", "Plank Căng Cơ", "Mục tiêu: 45 giây",
                "Cố định cơ thể thẳng hàng giúp siết chặt cơ bụng và lưng dưới.",
                "Thân trên & Core", "Bodyweight", 45,
                videoUrl = "asset:///videos/plank.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "sideplank", "Plank Nghiêng (Side Plank)", "Mục tiêu: 30 giây",
                "Nằm nghiêng nâng hông giữ thẳng thân để siết cơ liên sườn và eo.",
                "Thân trên & Core", "Bodyweight", 30,
                videoUrl = "asset:///videos/side_plank.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "jumpingjack", "Jumping Jacks", "Mục tiêu: 30 lần",
                "Đốt mỡ toàn thân và kích hoạt nhịp tim cực hiệu quả.",
                "Thân dưới & Cardio", "Bodyweight", 30,
                videoUrl = "asset:///videos/jumping_jack.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "splitsquat", "Ngồi Xổm Một Chân (Split Squat)", "Mục tiêu: 15 lần/bên",
                "Tăng thăng bằng, độ linh hoạt và phát triển cơ đùi săn chắc.",
                "Thân dưới & Cardio", "Bodyweight", 15,
                videoUrl = "asset:///videos/split_squat.mp4"
            )
        )
    }

    private fun setupRecyclerView() {
        adapter = LibraryExerciseAdapter(::showVideoTutorialDialog)
        binding.rvLibraryExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibraryExercises.adapter = adapter
        binding.rvLibraryExercises.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 160L
            removeDuration = 120L
            moveDuration = 160L
            changeDuration = 140L
            supportsChangeAnimations = false
        }
    }

    private fun setupSegmentedControls() {
        binding.btnTabNoEquipment.text = "TẤT CẢ (7)"
        binding.btnTabWithEquipment.text = "THEO NHÓM CƠ"
        binding.btnSubTabHome.text = "Thân trên & Core (4)"
        binding.btnSubTabGym.text = "Thân dưới & Cardio (3)"

        binding.btnTabNoEquipment.setOnClickListener {
            isEquipmentMode = false
            updateFilter()
        }
        binding.btnTabWithEquipment.setOnClickListener {
            isEquipmentMode = true
            updateFilter()
        }
        binding.btnSubTabHome.setOnClickListener {
            isGymMode = false
            updateFilter()
        }
        binding.btnSubTabGym.setOnClickListener {
            isGymMode = true
            updateFilter()
        }
    }

    private fun setupSearch() {
        binding.etLibrarySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                text: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchQuery = text?.toString().orEmpty()
                updateFilter()
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
    }

    private fun setupCustomExerciseButton() {
        binding.btnCreateCustom.setOnClickListener {
            findNavController().navigate(R.id.action_library_to_create_custom_plan)
        }
    }

    private fun updateFilter() {
        if (!isAdded || _binding == null) return

        applyTabStyle(binding.btnTabNoEquipment, !isEquipmentMode)
        applyTabStyle(binding.btnTabWithEquipment, isEquipmentMode)
        binding.layoutSubTabs.visibility = if (isEquipmentMode) View.VISIBLE else View.GONE

        if (isEquipmentMode) {
            applyTabStyle(binding.btnSubTabHome, !isGymMode)
            applyTabStyle(binding.btnSubTabGym, isGymMode)
        }

        val categoryFiltered = when {
            !isEquipmentMode -> allExercises
            !isGymMode -> allExercises.filter { it.category == "Thân trên & Core" }
            else -> allExercises.filter { it.category == "Thân dưới & Cardio" }
        }

        val normalizedQuery = normalizeText(searchQuery).trim()
        val finalFiltered = if (normalizedQuery.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { exercise ->
                normalizeText(
                    listOf(
                        exercise.name,
                        exercise.desc,
                        exercise.category,
                        exercise.equipment,
                        exercise.target
                    ).joinToString(" ")
                ).contains(normalizedQuery)
            }
        }

        adapter.submitList(finalFiltered)
        binding.tvLibraryResultCount.text = "${finalFiltered.size} bài tập"
        binding.rvLibraryExercises.visibility =
            if (finalFiltered.isEmpty()) View.GONE else View.VISIBLE
        binding.tvLibraryEmpty.visibility =
            if (finalFiltered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyTabStyle(button: MaterialButton, active: Boolean) {
        val context = requireContext()
        val background = if (active) R.color.tri_force_blue else R.color.tri_force_white
        val text = if (active) R.color.tri_force_white else R.color.tri_force_text_secondary
        val stroke = if (active) R.color.tri_force_blue else R.color.tri_force_stroke

        button.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, background))
        button.setTextColor(ContextCompat.getColor(context, text))
        button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, stroke))
        button.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thin)
    }

    private fun normalizeText(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.getDefault())
    }

    private fun showVideoTutorialDialog(exercise: LibraryExercise) {
        val url = exercise.videoUrl
        if (url.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Video hướng dẫn cho ${exercise.name} đang được cập nhật.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể mở ứng dụng YouTube.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_video_player)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val textureView = dialog.findViewById<TextureView>(R.id.fullscreenTextureView)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.fullscreenVideoProgress)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnFullscreenClose)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvFullscreenVideoTitle)
        val layoutCenterReplay = dialog.findViewById<View>(R.id.layoutCenterReplay)
        val cardReplayButton = dialog.findViewById<View>(R.id.cardReplayButton)

        tvTitle.text = "Hướng dẫn: ${exercise.name}"
        var mediaPlayer: MediaPlayer? = MediaPlayer()

        fun playFromStart() {
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                layoutCenterReplay.visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun initMediaPlayer(surface: Surface) {
            try {
                mediaPlayer?.reset()
                mediaPlayer?.setSurface(surface)
                if (url.startsWith("asset:///")) {
                    val assetPath = url.substringAfter("asset:///")
                    val assetDescriptor = requireContext().assets.openFd(assetPath)
                    mediaPlayer?.setDataSource(
                        assetDescriptor.fileDescriptor,
                        assetDescriptor.startOffset,
                        assetDescriptor.length
                    )
                    assetDescriptor.close()
                } else {
                    mediaPlayer?.setDataSource(requireContext(), Uri.parse(url))
                }

                mediaPlayer?.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                mediaPlayer?.isLooping = false
                mediaPlayer?.setOnPreparedListener { player ->
                    progressBar.visibility = View.GONE
                    layoutCenterReplay.visibility = View.GONE
                    player.start()
                }
                mediaPlayer?.setOnCompletionListener {
                    layoutCenterReplay.visibility = View.VISIBLE
                }
                mediaPlayer?.setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Lỗi khi phát video bài tập.", Toast.LENGTH_SHORT).show()
                    true
                }
                mediaPlayer?.prepareAsync()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Lỗi mở video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture, width: Int, height: Int
            ) {
                initMediaPlayer(Surface(surfaceTexture))
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }

        cardReplayButton.setOnClickListener { playFromStart() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class LibraryExerciseAdapter(
        private val onWatchVideoClick: (LibraryExercise) -> Unit
    ) : RecyclerView.Adapter<LibraryExerciseAdapter.ViewHolder>() {

        private val items = mutableListOf<LibraryExercise>()

        fun submitList(newItems: List<LibraryExercise>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLibraryExerciseBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: ItemLibraryExerciseBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LibraryExercise) {
                val context = binding.root.context
                if (item.isCustom) {
                    binding.tvLibCategoryBadge.text = "TỰ TẠO"
                    binding.tvLibCategoryBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.tri_force_warning)
                    )
                    binding.tvLibCategoryBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.tri_force_warning_bg)
                } else {
                    binding.tvLibCategoryBadge.text = item.category.uppercase(Locale.getDefault())
                    binding.tvLibCategoryBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.tri_force_blue)
                    )
                    binding.tvLibCategoryBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.tri_force_blue_soft)
                }

                binding.tvLibEquipmentTag.text = item.equipment
                binding.tvLibEquipmentTag.setTextColor(
                    ContextCompat.getColor(context, R.color.tri_force_text_secondary)
                )
                binding.tvLibEquipmentTag.backgroundTintList =
                    ContextCompat.getColorStateList(context, R.color.tri_force_surface_soft)
                binding.tvLibExerciseName.text = item.name
                binding.tvLibExerciseTarget.text = item.target
                binding.tvLibExerciseDesc.text = item.desc
                binding.btnLibWatchVideo.setOnClickListener { onWatchVideoClick(item) }
                binding.root.setOnClickListener { onWatchVideoClick(item) }
            }
        }
    }
}

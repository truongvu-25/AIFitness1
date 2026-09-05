package com.google.mediapipe.examples.poselandmarker.ui.fragment.library

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentLibraryBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemLibraryExerciseBinding
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCatalog
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCategory

data class LibraryExercise(
    val id: String,
    val name: String,
    val target: String,
    val desc: String,
    val category: ExerciseCategory,
    val categoryLabel: String,
    val equipment: String,
    val targetCount: Int,
    val videoUrl: String = "",
    val isCustom: Boolean = false
)

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private var isEquipmentMode = false
    private var isGymMode = false // false = Home, true = Gym

    private lateinit var adapter: LibraryExerciseAdapter
    private val allExercises = mutableListOf<LibraryExercise>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
        setupCustomExerciseButton()
        updateFilter()
    }

    private fun initExerciseDatabase() {
        allExercises.clear()
        allExercises += ExerciseCatalog.all(requireContext()).map { exercise ->
            LibraryExercise(
                id = exercise.id,
                name = exercise.name,
                target = exercise.targetLabel,
                desc = exercise.libraryDescription,
                category = exercise.category,
                categoryLabel = exercise.categoryLabel,
                equipment = exercise.equipment,
                targetCount = exercise.defaultTarget,
                videoUrl = exercise.videoUrl
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = LibraryExerciseAdapter { exercise ->
            showVideoTutorialDialog(exercise)
        }
        binding.rvLibraryExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibraryExercises.adapter = adapter
    }

    private fun showVideoTutorialDialog(exercise: LibraryExercise) {
        val url = exercise.videoUrl
        if (url.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.video_unavailable, exercise.name),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.video_youtube_unavailable, Toast.LENGTH_SHORT).show()
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

        tvTitle.text = getString(R.string.video_tutorial_for, exercise.name)

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
                    val afd = requireContext().assets.openFd(assetPath)
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                } else {
                    mediaPlayer?.setDataSource(requireContext(), Uri.parse(url))
                }

                mediaPlayer?.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                mediaPlayer?.isLooping = false

                mediaPlayer?.setOnPreparedListener { mp ->
                    progressBar.visibility = View.GONE
                    layoutCenterReplay.visibility = View.GONE
                    mp.start()
                }

                mediaPlayer?.setOnCompletionListener {
                    layoutCenterReplay.visibility = View.VISIBLE
                }

                mediaPlayer?.setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, R.string.video_play_error, Toast.LENGTH_SHORT).show()
                    true
                }

                mediaPlayer?.prepareAsync()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    context,
                    getString(R.string.video_open_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                val surface = Surface(surfaceTexture)
                initMediaPlayer(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
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
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        cardReplayButton.setOnClickListener {
            playFromStart()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

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

    private fun setupSegmentedControls() {
        binding.btnTabNoEquipment.setText(R.string.tab_no_equipment)
        binding.btnTabWithEquipment.setText(R.string.tab_with_equipment)
        binding.btnSubTabHome.setText(R.string.library_upper_count)
        binding.btnSubTabGym.setText(R.string.library_lower_count)

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

    private fun setupCustomExerciseButton() {
        binding.btnCreateCustom.setOnClickListener {
            findNavController().navigate(R.id.action_library_to_create_custom_plan)
        }
    }

    private fun updateFilter() {
        val activePrimaryColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary)
        val activeSecondaryColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary_variant)
        val transparentColor = Color.TRANSPARENT
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.tri_force_white)
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.tri_force_text_secondary)

        if (!isEquipmentMode) {
            // "Tất cả" Active -> Solid Blue Background
            binding.btnTabNoEquipment.backgroundTintList = ColorStateList.valueOf(activePrimaryColor)
            binding.btnTabNoEquipment.setTextColor(whiteColor)

            // "Theo Nhóm Cơ" Inactive -> Transparent Background
            binding.btnTabWithEquipment.backgroundTintList = ColorStateList.valueOf(transparentColor)
            binding.btnTabWithEquipment.setTextColor(inactiveTextColor)

            binding.layoutSubTabs.visibility = View.GONE

            adapter.submitList(allExercises)
        } else {
            // "Tất cả" Inactive -> Transparent Background
            binding.btnTabNoEquipment.backgroundTintList = ColorStateList.valueOf(transparentColor)
            binding.btnTabNoEquipment.setTextColor(inactiveTextColor)

            // "Theo Nhóm Cơ" Active -> Solid Blue Background
            binding.btnTabWithEquipment.backgroundTintList = ColorStateList.valueOf(activePrimaryColor)
            binding.btnTabWithEquipment.setTextColor(whiteColor)

            binding.layoutSubTabs.visibility = View.VISIBLE

            if (!isGymMode) {
                // "Thân trên & Core" Active -> Solid Cyan Background
                binding.btnSubTabHome.backgroundTintList = ColorStateList.valueOf(activeSecondaryColor)
                binding.btnSubTabHome.setTextColor(whiteColor)

                // "Thân dưới & Cardio" Inactive -> Transparent Background
                binding.btnSubTabGym.backgroundTintList = ColorStateList.valueOf(transparentColor)
                binding.btnSubTabGym.setTextColor(inactiveTextColor)

                val filtered = allExercises.filter { it.category == ExerciseCategory.UPPER_CORE }
                adapter.submitList(filtered)
            } else {
                // "Thân trên & Core" Inactive -> Transparent Background
                binding.btnSubTabHome.backgroundTintList = ColorStateList.valueOf(transparentColor)
                binding.btnSubTabHome.setTextColor(inactiveTextColor)

                // "Thân dưới & Cardio" Active -> Solid Cyan Background
                binding.btnSubTabGym.backgroundTintList = ColorStateList.valueOf(activeSecondaryColor)
                binding.btnSubTabGym.setTextColor(whiteColor)

                val filtered = allExercises.filter { it.category == ExerciseCategory.LOWER_CARDIO }
                adapter.submitList(filtered)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Inner Adapter
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

        inner class ViewHolder(private val binding: ItemLibraryExerciseBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LibraryExercise) {
                if (item.isCustom) {
                    binding.tvLibCategoryBadge.setText(R.string.library_custom_badge)
                    binding.tvLibCategoryBadge.setTextColor(Color.parseColor("#F59E0B"))
                    binding.tvLibCategoryBadge.setBackgroundColor(Color.parseColor("#26F59E0B"))
                } else {
                    binding.tvLibCategoryBadge.text = item.categoryLabel
                    binding.tvLibCategoryBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.mp_color_primary_variant))
                    binding.tvLibCategoryBadge.setBackgroundColor(Color.parseColor("#260066FF"))
                }

                binding.tvLibEquipmentTag.text = item.equipment
                binding.tvLibExerciseName.text = item.name
                binding.tvLibExerciseTarget.text = item.target
                binding.tvLibExerciseDesc.text = item.desc

                binding.btnLibWatchVideo.setOnClickListener {
                    onWatchVideoClick(item)
                }
            }
        }
    }
}

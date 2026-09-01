package com.google.mediapipe.examples.poselandmarker.fragment

import android.app.Dialog
import android.content.Context
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

data class LibraryExercise(
    val id: String,
    val name: String,
    val target: String,
    val desc: String,
    val category: String, // "Không dụng cụ", "Tại nhà", "Phòng gym"
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
        // 1. Không Dụng Cụ (Bodyweight) - With Custom Video Assets for Push-up & Sit-up
        allExercises.add(
            LibraryExercise(
                "pushup",
                "Hít Đất (Push-up)",
                "Mục tiêu: 15 lần",
                "Phát triển cơ ngực, vai và bắp tay sau toàn diện.",
                "Không dụng cụ",
                "Bodyweight",
                15,
                videoUrl = "asset:///videos/HitDatTriForce.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "situp",
                "Gập Bụng (Sit-up)",
                "Mục tiêu: 20 lần",
                "Tăng cường sức mạnh nhóm cơ bụng và core cốt lõi.",
                "Không dụng cụ",
                "Bodyweight",
                20,
                videoUrl = "asset:///videos/GapBungTriForce.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "squat",
                "Ngồi Xổm (Squats)",
                "Mục tiêu: 20 lần",
                "Xây dựng cơ đùi trước, đùi sau và cơ mông săn chắc.",
                "Không dụng cụ",
                "Bodyweight",
                20,
                videoUrl = "asset:///videos/SquatTriForce.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "plank",
                "Plank Căng Cơ",
                "Mục tiêu: 45 giây",
                "Cố định cơ thể thẳng hàng giúp siết chặt cơ bụng và lưng dưới.",
                "Không dụng cụ",
                "Bodyweight",
                45,
                videoUrl = "asset:///videos/PlankTriForce.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "jumping_jacks",
                "Jumping Jacks",
                "Mục tiêu: 30 lần",
                "Đốt mỡ toàn thân và kích hoạt nhịp tim cực hiệu quả.",
                "Không dụng cụ",
                "Bodyweight",
                30,
                videoUrl = "asset:///videos/jumping_jack.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "lunges",
                "Lunge Chùng Chân",
                "Mục tiêu: 15 lần/bên",
                "Tăng thăng bằng, độ linh hoạt và độ khỏe của khớp gối.",
                "Không dụng cụ",
                "Bodyweight",
                15,
                videoUrl = "asset:///videos/split_squat.mp4"
            )
        )
        allExercises.add(
            LibraryExercise(
                "mountain_climber",
                "Leo Núi (Mountain Climbers)",
                "Mục tiêu: 30 giây",
                "Đốt mỡ nhanh và tăng sức bền cơ bụng dưới.",
                "Không dụng cụ",
                "Bodyweight",
                30,
                videoUrl = "asset:///videos/plank.mp4"
            )
        )

        // 2. Có Dụng Cụ - Tại Nhà (Home Equipment)
        allExercises.add(LibraryExercise("db_curl", "Cuốn Tạ Tay (Dumbbell Curl)", "Mục tiêu: 12 lần", "Phát triển khối cơ bắp tay trước căng tròn.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))
        allExercises.add(LibraryExercise("db_shoulder_press", "Đẩy Vai Tạ Đơn (Shoulder Press)", "Mục tiêu: 12 lần", "Tạo cầu vai rộng, dày và khỏe mạnh.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))
        allExercises.add(LibraryExercise("goblet_squat", "Goblet Squat (Squat Ôm Tạ)", "Mục tiêu: 15 lần", "Squat có tải trọng kích thích đùi và mông tối đa.", "Tại nhà", "Tạ đơn (Dumbbell)", 15, videoUrl = "asset:///videos/SquatTriForce.mp4"))
        allExercises.add(LibraryExercise("tricep_dips", "Tricep Dips (Ghế Tựa)", "Mục tiêu: 15 lần", "Dùng ghế chắc chắn để ép sâu cơ tay sau.", "Tại nhà", "Ghế tập / Bậc thang", 15))
        allExercises.add(LibraryExercise("band_lateral_walk", "Bước Ngang Dây Kháng Lực", "Mục tiêu: 20 bước", "Kích hoạt cơ mông nhỡ và cải thiện hông cân đối.", "Tại nhà", "Dây Miniband", 20))
        allExercises.add(LibraryExercise("db_rdl", "Dumbbell Romanian Deadlift", "Mục tiêu: 12 lần", "Tác động sâu vào cơ đùi sau và chuỗi cơ lưng dưới.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))

        // 3. Có Dụng Cụ - Phòng Gym (Gym Equipment)
        allExercises.add(LibraryExercise("barbell_bench_press", "Nằm Đẩy Tạ Đòn (Bench Press)", "Mục tiêu: 10 lần", "Bài tập vua xây dựng độ dày và sức mạnh cơ ngực.", "Phòng gym", "Tạ đòn & Ghế phẳng", 10, videoUrl = "asset:///videos/HitDatTriForce.mp4"))
        allExercises.add(LibraryExercise("lat_pulldown", "Kéo Cáp Xô Lưng (Lat Pulldown)", "Mục tiêu: 12 lần", "Mở rộng lưng xô chữ V cuốn hút.", "Phòng gym", "Máy kéo cáp (Cable)", 12))
        allExercises.add(LibraryExercise("barbell_squat", "Gánh Tạ Đòn Squat (Barbell Squat)", "Mục tiêu: 10 lần", "Tăng khối lượng cơ bắp toàn bộ phần thân dưới.", "Phòng gym", "Khung gánh tạ đòn", 10, videoUrl = "asset:///videos/SquatTriForce.mp4"))
        allExercises.add(LibraryExercise("cable_tricep_pushdown", "Kéo Cáp Tay Sau (Pushdown)", "Mục tiêu: 12 lần", "Cô lập và siết nét cơ tay sau sắc cạnh.", "Phòng gym", "Dây thừng kéo cáp", 12))
        allExercises.add(LibraryExercise("leg_press", "Đạp Đùi Máy Nghiêng (Leg Press)", "Mục tiêu: 12 lần", "Đẩy tạ nặng an toàn cho khớp lưng và tối ưu đùi.", "Phòng gym", "Máy đạp đùi (Leg Press)", 12))
        allExercises.add(LibraryExercise("seated_cable_row", "Kéo Cáp Ngồi (Seated Row)", "Mục tiêu: 12 lần", "Làm dày cơ lưng giữa và cải thiện tư thế đứng thẳng.", "Phòng gym", "Máy chèo cáp (Row)", 12))
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
            Toast.makeText(requireContext(), "Video hướng dẫn cho ${exercise.name} đang được cập nhật.", Toast.LENGTH_SHORT).show()
            return
        }

        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
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

                var sourceSet = false
                if (url.startsWith("raw/")) {
                    val rawName = url.substringAfter("raw/")
                    val resId = requireContext().resources.getIdentifier(rawName, "raw", requireContext().packageName)
                    if (resId != 0) {
                        val afd = requireContext().resources.openRawResourceFd(resId)
                        mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        sourceSet = true
                    }
                } else if (url.startsWith("asset:///")) {
                    try {
                        val assetPath = url.substringAfter("asset:///")
                        val afd = requireContext().assets.openFd(assetPath)
                        mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        sourceSet = true
                    } catch (e: Exception) {
                        // Fallback to res/raw if asset is not found
                        val rawName = when (exercise.id) {
                            "pushup" -> "hit_dat_tri_force"
                            "situp" -> "gap_bung_tri_force"
                            "squat", "goblet_squat", "barbell_squat" -> "squat_tri_force"
                            "plank" -> "plank_tri_force"
                            else -> ""
                        }
                        if (rawName.isNotEmpty()) {
                            val resId = requireContext().resources.getIdentifier(rawName, "raw", requireContext().packageName)
                            if (resId != 0) {
                                val afd = requireContext().resources.openRawResourceFd(resId)
                                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                sourceSet = true
                            }
                        }
                    }
                }

                if (!sourceSet) {
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
        val silverColor = ContextCompat.getColor(requireContext(), R.color.tri_force_silver)

        if (!isEquipmentMode) {
            // "Không Dụng Cụ" Active -> Solid Blue Background
            binding.btnTabNoEquipment.backgroundTintList = ColorStateList.valueOf(activePrimaryColor)
            binding.btnTabNoEquipment.setTextColor(whiteColor)

            // "Có Dụng Cụ" Inactive -> Transparent Background
            binding.btnTabWithEquipment.backgroundTintList = ColorStateList.valueOf(transparentColor)
            binding.btnTabWithEquipment.setTextColor(silverColor)

            binding.layoutSubTabs.visibility = View.GONE

            val filtered = allExercises.filter { it.category == "Không dụng cụ" }
            adapter.submitList(filtered)
        } else {
            // "Không Dụng Cụ" Inactive -> Transparent Background
            binding.btnTabNoEquipment.backgroundTintList = ColorStateList.valueOf(transparentColor)
            binding.btnTabNoEquipment.setTextColor(silverColor)

            // "Có Dụng Cụ" Active -> Solid Blue Background
            binding.btnTabWithEquipment.backgroundTintList = ColorStateList.valueOf(activePrimaryColor)
            binding.btnTabWithEquipment.setTextColor(whiteColor)

            binding.layoutSubTabs.visibility = View.VISIBLE

            if (!isGymMode) {
                // "Tại Nhà" Active -> Solid Cyan Background
                binding.btnSubTabHome.backgroundTintList = ColorStateList.valueOf(activeSecondaryColor)
                binding.btnSubTabHome.setTextColor(whiteColor)

                // "Phòng Gym" Inactive -> Transparent Background
                binding.btnSubTabGym.backgroundTintList = ColorStateList.valueOf(transparentColor)
                binding.btnSubTabGym.setTextColor(silverColor)

                val filtered = allExercises.filter { it.category == "Tại nhà" }
                adapter.submitList(filtered)
            } else {
                // "Tại Nhà" Inactive -> Transparent Background
                binding.btnSubTabHome.backgroundTintList = ColorStateList.valueOf(transparentColor)
                binding.btnSubTabHome.setTextColor(silverColor)

                // "Phòng Gym" Active -> Solid Cyan Background
                binding.btnSubTabGym.backgroundTintList = ColorStateList.valueOf(activeSecondaryColor)
                binding.btnSubTabGym.setTextColor(whiteColor)

                val filtered = allExercises.filter { it.category == "Phòng gym" }
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
                    binding.tvLibCategoryBadge.text = "⭐ Tự tạo"
                    binding.tvLibCategoryBadge.setTextColor(Color.parseColor("#F59E0B"))
                    binding.tvLibCategoryBadge.setBackgroundColor(Color.parseColor("#26F59E0B"))
                } else {
                    binding.tvLibCategoryBadge.text = item.category
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

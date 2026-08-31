package com.google.mediapipe.examples.poselandmarker.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    val targetCount: Int
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
        updateFilter()
    }

    private fun initExerciseDatabase() {
        allExercises.clear()
        // 1. Không Dụng Cụ (Bodyweight)
        allExercises.add(LibraryExercise("pushup", "Hít Đất (Push-up)", "Mục tiêu: 15 lần", "Phát triển cơ ngực, vai và bắp tay sau toàn diện.", "Không dụng cụ", "Bodyweight", 15))
        allExercises.add(LibraryExercise("situp", "Gập Bụng (Sit-up)", "Mục tiêu: 20 lần", "Tăng cường sức mạnh nhóm cơ bụng và core cốt lõi.", "Không dụng cụ", "Bodyweight", 20))
        allExercises.add(LibraryExercise("squat", "Ngồi Xổm (Squats)", "Mục tiêu: 20 lần", "Xây dựng cơ đùi trước, đùi sau và cơ mông săn chắc.", "Không dụng cụ", "Bodyweight", 20))
        allExercises.add(LibraryExercise("plank", "Plank Căng Cơ", "Mục tiêu: 45 giây", "Cố định cơ thể thẳng hàng giúp siết chặt cơ bụng và lưng dưới.", "Không dụng cụ", "Bodyweight", 45))
        allExercises.add(LibraryExercise("jumping_jacks", "Jumping Jacks", "Mục tiêu: 30 lần", "Đốt mỡ toàn thân và kích hoạt nhịp tim cực hiệu quả.", "Không dụng cụ", "Bodyweight", 30))
        allExercises.add(LibraryExercise("lunges", "Lunge Chùng Chân", "Mục tiêu: 15 lần/bên", "Tăng thăng bằng, độ linh hoạt và độ khỏe của khớp gối.", "Không dụng cụ", "Bodyweight", 15))
        allExercises.add(LibraryExercise("mountain_climber", "Leo Núi (Mountain Climbers)", "Mục tiêu: 30 giây", "Đốt mỡ nhanh và tăng sức bền cơ bụng dưới.", "Không dụng cụ", "Bodyweight", 30))

        // 2. Có Dụng Cụ - Tại Nhà (Home Equipment)
        allExercises.add(LibraryExercise("db_curl", "Cuốn Tạ Tay (Dumbbell Curl)", "Mục tiêu: 12 lần", "Phát triển khối cơ bắp tay trước căng tròn.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))
        allExercises.add(LibraryExercise("db_shoulder_press", "Đẩy Vai Tạ Đơn (Shoulder Press)", "Mục tiêu: 12 lần", "Tạo cầu vai rộng, dày và khỏe mạnh.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))
        allExercises.add(LibraryExercise("goblet_squat", "Goblet Squat (Squat Ôm Tạ)", "Mục tiêu: 15 lần", "Squat có tải trọng kích thích đùi và mông tối đa.", "Tại nhà", "Tạ đơn (Dumbbell)", 15))
        allExercises.add(LibraryExercise("tricep_dips", "Tricep Dips (Ghế Tựa)", "Mục tiêu: 15 lần", "Dùng ghế chắc chắn để ép sâu cơ tay sau.", "Tại nhà", "Ghế tập / Bậc thang", 15))
        allExercises.add(LibraryExercise("band_lateral_walk", "Bước Ngang Dây Kháng Lực", "Mục tiêu: 20 bước", "Kích hoạt cơ mông nhỡ và cải thiện hông cân đối.", "Tại nhà", "Dây Miniband", 20))
        allExercises.add(LibraryExercise("db_rdl", "Dumbbell Romanian Deadlift", "Mục tiêu: 12 lần", "Tác động sâu vào cơ đùi sau và chuỗi cơ lưng dưới.", "Tại nhà", "Tạ đơn (Dumbbell)", 12))

        // 3. Có Dụng Cụ - Phòng Gym (Gym Equipment)
        allExercises.add(LibraryExercise("barbell_bench_press", "Nằm Đẩy Tạ Đòn (Bench Press)", "Mục tiêu: 10 lần", "Bài tập vua xây dựng độ dày và sức mạnh cơ ngực.", "Phòng gym", "Tạ đòn & Ghế phẳng", 10))
        allExercises.add(LibraryExercise("lat_pulldown", "Kéo Cáp Xô Lưng (Lat Pulldown)", "Mục tiêu: 12 lần", "Mở rộng lưng xô chữ V cuốn hút.", "Phòng gym", "Máy kéo cáp (Cable)", 12))
        allExercises.add(LibraryExercise("barbell_squat", "Gánh Tạ Đòn Squat (Barbell Squat)", "Mục tiêu: 10 lần", "Tăng khối lượng cơ bắp toàn bộ phần thân dưới.", "Phòng gym", "Khung gánh tạ đòn", 10))
        allExercises.add(LibraryExercise("cable_tricep_pushdown", "Kéo Cáp Tay Sau (Pushdown)", "Mục tiêu: 12 lần", "Cô lập và siết nét cơ tay sau sắc cạnh.", "Phòng gym", "Dây thừng kéo cáp", 12))
        allExercises.add(LibraryExercise("leg_press", "Đạp Đùi Máy Nghiêng (Leg Press)", "Mục tiêu: 12 lần", "Đẩy tạ nặng an toàn cho khớp lưng và tối ưu đùi.", "Phòng gym", "Máy đạp đùi (Leg Press)", 12))
        allExercises.add(LibraryExercise("seated_cable_row", "Kéo Cáp Ngồi (Seated Row)", "Mục tiêu: 12 lần", "Làm dày cơ lưng giữa và cải thiện tư thế đứng thẳng.", "Phòng gym", "Máy chèo cáp (Row)", 12))
    }

    private fun setupRecyclerView() {
        adapter = LibraryExerciseAdapter { exercise ->
            // Launch Camera Workout AI HUD
            try {
                val action = LibraryFragmentDirections.actionLibraryToCamera(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    targetCount = exercise.targetCount,
                    dayIndex = 1
                )
                findNavController().navigate(action)
            } catch (e: Exception) {
                val bundle = Bundle().apply {
                    putString("exerciseId", exercise.id)
                    putString("exerciseName", exercise.name)
                    putInt("targetCount", exercise.targetCount)
                    putInt("dayIndex", 1)
                }
                findNavController().navigate(R.id.camera_fragment, bundle)
            }
        }
        binding.rvLibraryExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibraryExercises.adapter = adapter
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

    private fun updateFilter() {
        val activeBgColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary)
        val activeCyanColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary_variant)
        val transparentColor = Color.TRANSPARENT
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.tri_force_white)
        val silverColor = ContextCompat.getColor(requireContext(), R.color.tri_force_silver)

        if (!isEquipmentMode) {
            // "Không Dụng Cụ" Selected
            binding.btnTabNoEquipment.setBackgroundColor(activeBgColor)
            binding.btnTabNoEquipment.setTextColor(whiteColor)
            binding.btnTabWithEquipment.setBackgroundColor(transparentColor)
            binding.btnTabWithEquipment.setTextColor(silverColor)

            binding.layoutSubTabs.visibility = View.GONE

            val filtered = allExercises.filter { it.category == "Không dụng cụ" }
            adapter.submitList(filtered)
        } else {
            // "Có Dụng Cụ" Selected
            binding.btnTabNoEquipment.setBackgroundColor(transparentColor)
            binding.btnTabNoEquipment.setTextColor(silverColor)
            binding.btnTabWithEquipment.setBackgroundColor(activeBgColor)
            binding.btnTabWithEquipment.setTextColor(whiteColor)

            binding.layoutSubTabs.visibility = View.VISIBLE

            if (!isGymMode) {
                // "Tại Nhà" Selected
                binding.btnSubTabHome.setBackgroundColor(activeCyanColor)
                binding.btnSubTabHome.setTextColor(whiteColor)
                binding.btnSubTabGym.setBackgroundColor(transparentColor)
                binding.btnSubTabGym.setTextColor(silverColor)

                val filtered = allExercises.filter { it.category == "Tại nhà" }
                adapter.submitList(filtered)
            } else {
                // "Phòng Gym" Selected
                binding.btnSubTabHome.setBackgroundColor(transparentColor)
                binding.btnSubTabHome.setTextColor(silverColor)
                binding.btnSubTabGym.setBackgroundColor(activeCyanColor)
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
        private val onStartClick: (LibraryExercise) -> Unit
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
                binding.tvLibCategoryBadge.text = item.category
                binding.tvLibEquipmentTag.text = item.equipment
                binding.tvLibExerciseName.text = item.name
                binding.tvLibExerciseTarget.text = item.target
                binding.tvLibExerciseDesc.text = item.desc

                binding.btnLibWatchVideo.setOnClickListener {
                    Toast.makeText(
                        binding.root.context,
                        "Hướng dẫn bài tập: ${item.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                binding.btnLibStartExercise.setOnClickListener {
                    onStartClick(item)
                }
            }
        }
    }
}

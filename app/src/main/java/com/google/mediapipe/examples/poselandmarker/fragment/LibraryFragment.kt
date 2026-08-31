package com.google.mediapipe.examples.poselandmarker.fragment

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentLibraryBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemLibraryExerciseBinding
import org.json.JSONArray
import org.json.JSONObject

data class LibraryExercise(
    val id: String,
    val name: String,
    val target: String,
    val desc: String,
    val category: String, // "Không dụng cụ", "Tại nhà", "Phòng gym"
    val equipment: String,
    val targetCount: Int,
    val isCustom: Boolean = false
)

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private var isEquipmentMode = false
    private var isGymMode = false // false = Home, true = Gym

    private lateinit var adapter: LibraryExerciseAdapter
    private val allExercises = mutableListOf<LibraryExercise>()

    private val PREF_CUSTOM_EXERCISES = "tri_force_custom_exercises"

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

        // 4. Load Saved Custom Exercises
        loadCustomExercises()
    }

    private fun loadCustomExercises() {
        val prefs = requireContext().getSharedPreferences(PREF_CUSTOM_EXERCISES, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("custom_list", null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                allExercises.add(
                    LibraryExercise(
                        id = obj.optString("id", "custom_${System.currentTimeMillis()}"),
                        name = obj.optString("name", "Bài tập tự tạo"),
                        target = obj.optString("target", "Mục tiêu: 15 lần"),
                        desc = obj.optString("desc", "Bài tập tự tùy chỉnh theo ý muốn."),
                        category = obj.optString("category", "Không dụng cụ"),
                        equipment = obj.optString("equipment", "Bodyweight"),
                        targetCount = obj.optInt("targetCount", 15),
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCustomExercise(exercise: LibraryExercise) {
        val prefs = requireContext().getSharedPreferences(PREF_CUSTOM_EXERCISES, Context.MODE_PRIVATE)
        val existingJson = prefs.getString("custom_list", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(existingJson)
            val newObj = JSONObject().apply {
                put("id", exercise.id)
                put("name", exercise.name)
                put("target", exercise.target)
                put("desc", exercise.desc)
                put("category", exercise.category)
                put("equipment", exercise.equipment)
                put("targetCount", exercise.targetCount)
            }
            jsonArray.put(newObj)
            prefs.edit().putString("custom_list", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        adapter = LibraryExerciseAdapter()
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

    private fun setupCustomExerciseButton() {
        binding.btnCreateCustom.setOnClickListener {
            findNavController().navigate(R.id.action_library_to_create_custom_plan)
        }
    }

    private fun showCreateCustomExerciseDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_create_custom_exercise)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<TextInputEditText>(R.id.etCustomName)
        val rgCategory = dialog.findViewById<RadioGroup>(R.id.rgCategory)
        val rbNoEquipment = dialog.findViewById<RadioButton>(R.id.rbNoEquipment)
        val rbHome = dialog.findViewById<RadioButton>(R.id.rbHome)
        val rbGym = dialog.findViewById<RadioButton>(R.id.rbGym)
        val etEquipment = dialog.findViewById<TextInputEditText>(R.id.etCustomEquipment)
        val etTarget = dialog.findViewById<TextInputEditText>(R.id.etCustomTarget)
        val etDesc = dialog.findViewById<TextInputEditText>(R.id.etCustomDesc)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelCustom)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveCustom)

        rgCategory.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbNoEquipment -> etEquipment.setText("Bodyweight")
                R.id.rbHome -> etEquipment.setText("Tạ đơn / Dây kháng lực")
                R.id.rbGym -> etEquipment.setText("Tạ đòn / Máy tập Gym")
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                etName.error = "Vui lòng nhập tên bài tập"
                return@setOnClickListener
            }

            val category = when {
                rbHome.isChecked -> "Tại nhà"
                rbGym.isChecked -> "Phòng gym"
                else -> "Không dụng cụ"
            }

            val equipment = etEquipment.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "Tự chọn" else it
            }

            val target = etTarget.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "Mục tiêu: 15 lần" else it
            }

            val desc = etDesc.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "Bài tập cá nhân hóa theo ý muốn." else it
            }

            val newExercise = LibraryExercise(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                target = target,
                desc = desc,
                category = category,
                equipment = equipment,
                targetCount = 15,
                isCustom = true
            )

            allExercises.add(0, newExercise)
            saveCustomExercise(newExercise)

            Toast.makeText(requireContext(), "Đã thêm bài tập \"$name\" vào thư viện!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            // Navigate tab to match the category created
            if (category == "Không dụng cụ") {
                isEquipmentMode = false
            } else {
                isEquipmentMode = true
                isGymMode = (category == "Phòng gym")
            }
            updateFilter()
        }

        dialog.show()
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
    class LibraryExerciseAdapter : RecyclerView.Adapter<LibraryExerciseAdapter.ViewHolder>() {

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
                    Toast.makeText(
                        binding.root.context,
                        "Đang mở video hướng dẫn: ${item.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

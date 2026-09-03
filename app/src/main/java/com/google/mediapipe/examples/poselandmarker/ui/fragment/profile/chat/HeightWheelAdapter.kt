package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.databinding.ItemWheelNumberBinding

class HeightWheelAdapter(
    private val values: List<Int>
) : RecyclerView.Adapter<HeightWheelAdapter.ValueViewHolder>() {

    var centerValue: Int = values.firstOrNull() ?: 0
        private set

    inner class ValueViewHolder(val binding: ItemWheelNumberBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
        val binding = ItemWheelNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ValueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
        val value = values[position]
        holder.binding.tvWheelValue.text = value.toString()
        updateItemStyle(holder, value == centerValue)
    }

    override fun getItemCount(): Int = values.size

    fun updateItemStyle(holder: ValueViewHolder, isSelected: Boolean) {
        if (isSelected) {
            holder.binding.tvWheelValue.textSize = 24f
            holder.binding.tvWheelValue.setTextColor(
                holder.binding.root.context.getColor(com.google.mediapipe.examples.poselandmarker.R.color.tri_force_text_primary)
            )
            holder.binding.tvWheelValue.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            holder.binding.tvWheelValue.textSize = 18f
            holder.binding.tvWheelValue.setTextColor(
                holder.binding.root.context.getColor(com.google.mediapipe.examples.poselandmarker.R.color.tri_force_slate)
            )
            holder.binding.tvWheelValue.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    /** Gọi mỗi khi cuộn dừng lại, để cập nhật số nào đang là "trung tâm". */
    fun setCenterValue(newCenterValue: Int, recyclerView: RecyclerView) {
        if (newCenterValue == centerValue) return
        centerValue = newCenterValue
        notifyDataSetChanged()
    }

    fun indexOf(value: Int): Int = values.indexOf(value)
}
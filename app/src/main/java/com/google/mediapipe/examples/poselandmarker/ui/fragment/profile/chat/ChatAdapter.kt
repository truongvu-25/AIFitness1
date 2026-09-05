package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.ItemChatBotMessageBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemChatTypingIndicatorBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemChatUserAnswerBinding

class ChatAdapter(
    private val items: MutableList<ChatItem> = mutableListOf(),
    private val onEditClicked: (questionIndex: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_BOT = 0
        private const val TYPE_USER = 1
        private const val TYPE_TYPING = 2
    }

    inner class BotViewHolder(val binding: ItemChatBotMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class UserViewHolder(val binding: ItemChatUserAnswerBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class TypingViewHolder(val binding: ItemChatTypingIndicatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var animatorSet: AnimatorSet? = null

        fun startDotsAnimation() {
            val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
            val animators = dots.mapIndexed { index, dot ->
                ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f).apply {
                    duration = 900
                    startDelay = index * 150L
                    repeatCount = ObjectAnimator.INFINITE
                }
            }
            animatorSet = AnimatorSet().apply {
                playTogether(animators as List<android.animation.Animator>)
                start()
            }
        }

        fun stopDotsAnimation() {
            animatorSet?.cancel()
            animatorSet = null
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ChatItem.BotMessage -> TYPE_BOT
            is ChatItem.UserAnswer -> TYPE_USER
            is ChatItem.TypingIndicator -> TYPE_TYPING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BOT -> BotViewHolder(ItemChatBotMessageBinding.inflate(inflater, parent, false))
            TYPE_USER -> UserViewHolder(ItemChatUserAnswerBinding.inflate(inflater, parent, false))
            else -> TypingViewHolder(ItemChatTypingIndicatorBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val enterAnim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.chat_bubble_enter)
        holder.itemView.startAnimation(enterAnim)

        when (val item = items[position]) {
            is ChatItem.BotMessage -> {
                (holder as BotViewHolder).binding.tvBotMessage.text = item.text
            }
            is ChatItem.UserAnswer -> {
                (holder as UserViewHolder).binding.tvUserAnswer.text = item.text
                holder.binding.tvEditAnswer.setOnClickListener {
                    onEditClicked(item.questionIndex)
                }
            }
            is ChatItem.TypingIndicator -> {
                (holder as TypingViewHolder).startDotsAnimation()
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TypingViewHolder) holder.stopDotsAnimation()
    }

    override fun getItemCount(): Int = items.size

    fun addItem(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeLastIfTyping() {
        if (items.isNotEmpty() && items.last() is ChatItem.TypingIndicator) {
            val pos = items.size - 1
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }

    fun removeFromIndex(fromPosition: Int) {
        val count = items.size - fromPosition
        if (count <= 0) return
        for (i in items.size - 1 downTo fromPosition) items.removeAt(i)
        notifyItemRangeRemoved(fromPosition, count)
    }

    fun clearItems() {
        val count = items.size
        if (count == 0) return
        items.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun getItems(): List<ChatItem> = items
}

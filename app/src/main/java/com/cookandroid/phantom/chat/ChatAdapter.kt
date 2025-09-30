package com.cookandroid.phantom.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.R
import com.cookandroid.phantom.ChatbotConversationResponse
import com.cookandroid.phantom.ChatbotMessageResponse

class ChatAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_BOT = 0
        private const val VIEW_USER = 1
        private const val VIEW_TYPING = 2
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position].sender) {
            Sender.USER -> VIEW_USER
            Sender.BOT -> VIEW_BOT
            Sender.TYPING -> VIEW_TYPING
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER -> UserVH(inf.inflate(R.layout.item_chat_user, parent, false))
            VIEW_TYPING -> TypingVH(inf.inflate(R.layout.item_chat_typing, parent, false))
            else -> BotVH(inf.inflate(R.layout.item_chat_bot, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserVH -> holder.tv.text = msg.text
            is BotVH -> holder.tv.text = msg.text
            is TypingVH -> { /* 입력 중 표시 */ }
        }
    }

    // ----- 공개 메서드 -----

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    fun addAllFromHistory(history: List<ChatbotConversationResponse>) {
        history.forEach { conv ->
            conv.messages.forEach { m ->
                items.add(
                    ChatMessage(
                        text = m.text,
                        sender = if (m.sender.equals("USER", true)) Sender.USER else Sender.BOT
                    )
                )
            }
        }
        notifyDataSetChanged()
    }

    /** 백엔드 메시지 응답을 RecyclerView에 추가 */
    fun addFromResponse(res: ChatbotMessageResponse) {
        items.add(ChatMessage(res.reply, Sender.BOT)) // ✅ DTO의 reply 필드 사용
        notifyItemInserted(items.lastIndex)
    }

    fun removeLastIfTyping() {
        if (items.isNotEmpty() && items.last().sender == Sender.TYPING) {
            val idx = items.lastIndex
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    // ----- ViewHolder -----
    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvMsg)
    }
    class BotVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvMsg)
    }
    class TypingVH(v: View) : RecyclerView.ViewHolder(v)
}

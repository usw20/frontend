package com.cookandroid.phantom.chat

enum class Sender { USER, BOT, TYPING }

data class ChatMessage(
    val text: String,
    val sender: Sender
)

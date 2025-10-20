package com.cookandroid.phantom.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData

class CopyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 우선순위: RAW -> EXTRA_TEXT
        val raw = intent.getStringExtra("com.cookandroid.phantom.EXTRA_RAW_TEXT")
        val provided = intent.getStringExtra(MyNotificationListener.EXTRA_TEXT)
            ?: intent.getStringExtra("com.cookandroid.phantom.EXTRA_TEXT")
        val source = raw ?: provided ?: return

        // 혹시 표시용 문자열이 들어왔을 경우를 대비해 "본문만" 추출
        val cleaned = extractBodyOnly(source)

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Phantom", cleaned))
        Toast.makeText(context, "텍스트를 복사했어요.", Toast.LENGTH_SHORT).show()
    }

    // 표시용에 '의심:' / '앱:' / '이유:' 헤더가 섞인 경우 제거해서 실제 본문만 반환
    private fun extractBodyOnly(src: String): String {
        // 1) 줄 단위로 나눠 앞쪽 메타 줄 제거
        val lines = src.lines()
            .filter { line ->
                val t = line.trim()
                // 메타 라인들 제거
                !(t.startsWith("의심:") || t.startsWith("앱:") || t.startsWith("이유:"))
            }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return src.trim()

        // 2) 남은 줄 중 "가장 긴 줄"을 본문으로 추정 (미리보기/번호 등보다 보통 길다)
        val longest = lines.maxByOrNull { it.length }!!.trim()

        // 3) 혹시 "앱: ... \n본문" 형태라면 마지막 줄이 본문일 가능성도 큼 → 더 길면 longest 유지
        val last = lines.last().trim()
        return if (last.length > longest.length / 2) last else longest
    }
}

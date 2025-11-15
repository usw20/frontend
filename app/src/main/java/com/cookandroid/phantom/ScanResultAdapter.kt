package com.cookandroid.phantom

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.model.ScanResult

class ScanResultAdapter(
    private var results: List<ScanResult>,
    private val onItemClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<ScanResultAdapter.ResultViewHolder>() {

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvThreatType: TextView = itemView.findViewById(R.id.tvThreatType)
        val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(results[position])
                }
            }
        }

        fun bind(result: ScanResult) {
            val context = itemView.context

            // 아이콘 로드
            try {
                val icon = context.packageManager.getApplicationIcon(result.appInfo.packageName)
                ivIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            tvName.text = result.appInfo.appName
            val confidencePercent = String.format("%.1f%%", result.confidence * 100)

            // 오류 및 악성 상태 표시 로직
            when {
                // 🚨 검사 오류 상태
                result.threatType?.contains("Error", ignoreCase = true) == true ||
                        result.threatType?.contains("Timeout", ignoreCase = true) == true ||
                        result.threatType?.contains("Unknown Host", ignoreCase = true) == true ||
                        result.threatType?.contains("Connection Error", ignoreCase = true) == true -> {
                    tvThreatType.text = "검사 오류: ${result.threatType}"
                    tvThreatType.setTextColor(Color.GRAY)
                    tvConfidence.text = "상태 확인 불가"
                    tvConfidence.setTextColor(Color.GRAY)
                }

                // ⚠️ 악성코드
                result.isMalicious -> {
                    val threatTypeText = when {
                        result.threatType.isNullOrBlank() -> "악성코드"
                        result.threatType == "Unknown" -> "의심스러운 앱"
                        else -> result.threatType
                    }
                    tvThreatType.text = "위험: $threatTypeText"
                    tvThreatType.setTextColor(Color.RED)
                    tvConfidence.text = "의심도: $confidencePercent"
                    tvConfidence.setTextColor(Color.RED)
                }

                // ✅ 안전
                else -> {
                    tvThreatType.text = "안전" // ← 수정: "상태: Unknown" 대신 "안전"으로 표시
                    tvThreatType.setTextColor(Color.parseColor("#00AA00"))
                    tvConfidence.text = "신뢰도: $confidencePercent"
                    tvConfidence.setTextColor(Color.parseColor("#999999"))
                }
            }
        }
    }

    fun updateData(newResults: List<ScanResult>) {
        this.results = newResults
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size
}
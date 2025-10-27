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
    // ⭐️ 데이터를 갱신할 수 있도록 'val'에서 'private var'로 변경합니다.
    private var results: List<ScanResult>,
    private val onItemClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<ScanResultAdapter.ResultViewHolder>() {

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // 뷰 초기화
        val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvThreatType: TextView = itemView.findViewById(R.id.tvThreatType)
        val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)

        init {
            // 항목 클릭 리스너 설정
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(results[position])
                }
            }
        }

        fun bind(result: ScanResult) {
            val context = itemView.context

            // 아이콘 로드 로직
            try {
                val icon = context.packageManager.getApplicationIcon(result.appInfo.packageName)
                ivIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            tvName.text = result.appInfo.appName
            val confidencePercent = String.format("%.1f%%", result.confidence * 100)

            // 오류 및 악성 상태 표시 로직
            if (result.threatType.contains("Error") ||
                result.threatType.contains("Timeout") ||
                result.threatType.contains("Unknown Host") ||
                result.threatType.contains("Connection Error")) {

                // 🚨 검사 오류 상태: 회색 경고
                tvThreatType.text = "검사 오류: ${result.threatType}"
                tvThreatType.setTextColor(Color.GRAY)
                tvConfidence.text = "상태 확인 불가"
                tvConfidence.setTextColor(Color.GRAY)
            }
            else if (result.isMalicious) {
                // ⚠️ 악성코드: 빨간색
                tvThreatType.text = "위험 요소 감지 (${result.threatType})"
                tvThreatType.setTextColor(Color.RED)
                tvConfidence.setTextColor(Color.RED)
                tvConfidence.text = "의심도: ${confidencePercent}"
            }
            else {
                // ✅ 안전: 초록색
                tvThreatType.text = "상태: ${result.threatType}"
                tvThreatType.setTextColor(Color.parseColor("#00AA00"))
                tvConfidence.setTextColor(Color.parseColor("#999999"))
                tvConfidence.text = "신뢰도: ${confidencePercent}"
            }
        }
    }

    // ⭐️ 데이터 업데이트 및 RecyclerView 새로고침 함수 (ScanResultActivity에서 호출됨)
    fun updateData(newResults: List<ScanResult>) {
        this.results = newResults
        // 데이터가 변경되었음을 RecyclerView에 알립니다.
        notifyDataSetChanged()
    }

    // 필수 오버라이드 함수
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
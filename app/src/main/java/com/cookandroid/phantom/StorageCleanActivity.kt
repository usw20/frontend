package com.cookandroid.phantom

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class StorageCleanActivity : AppCompatActivity() {

    private lateinit var tvLarge: TextView
    private lateinit var tvDup: TextView
    private lateinit var tvOld: TextView
    private lateinit var btnScan: Button
    private lateinit var btnClean: Button
    private lateinit var list: ListView
    private lateinit var btnBack: ImageButton

    private val items = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var lastClick = 0L
    private fun safeClick(run: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastClick > 400) { lastClick = now; run() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage_clean)
        supportActionBar?.hide()

        tvLarge = findViewById(R.id.tvLargeFiles)
        tvDup   = findViewById(R.id.tvDuplicate)
        tvOld   = findViewById(R.id.tvOld)
        btnScan = findViewById(R.id.btnScan)
        btnClean= findViewById(R.id.btnClean)
        list    = findViewById(R.id.listResults)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_multiple_choice, items)
        list.adapter = adapter

        btnScan.setOnClickListener { safeClick { fakeScan() } }
        btnClean.setOnClickListener { safeClick { cleanSelected() } }
        list.setOnItemClickListener { _, _, _, _ ->
            btnClean.isEnabled = list.checkedItemCount > 0
        }

        // 상태 복원
        if (savedInstanceState != null) {
            tvLarge.text = savedInstanceState.getString("large", "0개")
            tvDup.text   = savedInstanceState.getString("dup", "0개")
            tvOld.text   = savedInstanceState.getString("old", "0개")
            items.clear()
            items.addAll(savedInstanceState.getStringArrayList("items") ?: arrayListOf())
            adapter.notifyDataSetChanged()
            btnClean.isEnabled = savedInstanceState.getBoolean("cleanEnabled", false)
            isScanning = savedInstanceState.getBoolean("isScanning", false)
            setScanningUi(isScanning)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("large", tvLarge.text?.toString() ?: "0개")
        outState.putString("dup", tvDup.text?.toString() ?: "0개")
        outState.putString("old", tvOld.text?.toString() ?: "0개")
        outState.putStringArrayList("items", ArrayList(items))
        outState.putBoolean("cleanEnabled", btnClean.isEnabled)
        outState.putBoolean("isScanning", isScanning)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun setScanningUi(scanning: Boolean) {
        btnScan.isEnabled = !scanning
        btnScan.text = if (scanning) "스캔 중..." else "용량 스캔 시작"
        if (scanning) btnClean.isEnabled = false
    }

    /** 데모 스캔 */
    private fun fakeScan() {
        if (isScanning) return
        isScanning = true
        setScanningUi(true)

        uiHandler.postDelayed({
            tvLarge.text = "5개"
            tvDup.text   = "12개"
            tvOld.text   = "8개"

            items.clear()
            items.addAll(listOf(
                "📦 850MB - 영상_2023-07-01.mp4 (큰 파일)",
                "🖼️ 2.3MB - IMG_0001.jpg (중복)",
                "🖼️ 2.3MB - IMG_0001(1).jpg (중복)",
                "📄 15MB - 다운로드/large.zip (오래됨)",
                "📦 620MB - 게임캐시.tmp (큰 파일)",
                "🖼️ 4.1MB - 스크린샷_2022-02-14.png (오래됨)"
            ))
            adapter.notifyDataSetChanged()
            for (i in 0 until items.size) list.setItemChecked(i, false)

            isScanning = false
            setScanningUi(false)
            Toast.makeText(this, "스캔 완료 (예시 데이터)", Toast.LENGTH_SHORT).show()
        }, 1500)
    }

    /** 데모 정리 */
    private fun cleanSelected() {
        val selected = buildList {
            for (i in 0 until items.size) if (list.isItemChecked(i)) add(i)
        }.sortedDescending()
        if (selected.isEmpty()) return

        selected.forEach { idx -> if (idx in items.indices) items.removeAt(idx) }
        adapter.notifyDataSetChanged()
        for (i in 0 until items.size) list.setItemChecked(i, false)
        btnClean.isEnabled = false
        Toast.makeText(this, "선택 항목 정리 완료 (예시)", Toast.LENGTH_SHORT).show()
    }
}

package com.cookandroid.phantom

import android.app.Dialog
import android.graphics.Point
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class FullLogsDialogFragment : DialogFragment() {

    // ---- API 정의 (SecurityActivity와 동일) ----
    interface MalwareReadApi {
        @GET("/api/malware/history") suspend fun history(): Response<List<SecurityActivity.MalwareScanLogDto>>
    }
    interface PhishingReadApi {
        @GET("/api/phishing/history") suspend fun history(): Response<List<SecurityActivity.PhishingScanLogDto>>
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var rv: RecyclerView
    private lateinit var empty: LinearLayout
    private lateinit var btnClose: TextView

    private fun getToken(): String? =
        requireContext().getSharedPreferences("phantom_prefs", android.content.Context.MODE_PRIVATE)
            .getString("jwt_token", null)

    private fun retrofit(): Retrofit {
        val auth = Interceptor { chain ->
            val t = getToken()
            val req = if (!t.isNullOrBlank())
                chain.request().newBuilder().addHeader("Authorization", "Bearer $t").build()
            else chain.request()
            chain.proceed(req)
        }
        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .client(OkHttpClient.Builder().addInterceptor(auth).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = Dialog(requireContext())
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_full_logs)

        // 뷰
        rv = d.findViewById(R.id.rvFullLogs)
        empty = d.findViewById(R.id.emptyView)
        btnClose = d.findViewById(R.id.btnClose)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = SecurityActivity.SecurityLogAdapter()

        btnClose.setOnClickListener { dismiss() }

        // 데이터 로드
        loadAllLogs()

        // 창 배치(가운데, 너비/높이 조정 & 배경 딤)
        d.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            attributes = attributes.apply {
                dimAmount = 0.35f
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            // 화면 90% 너비, 높이 최대 80%
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            setLayout((size.x * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        return d
    }

    private fun loadAllLogs() {
        uiScope.launch {
            try {
                val r = retrofit()
                val malware = withContext(Dispatchers.IO) {
                    r.create(MalwareReadApi::class.java).history().body().orEmpty().map {
                        SecurityActivity.UnifiedLog(
                            type = "malware",
                            title = it.targetPackageName ?: "알 수 없는 앱",
                            result = it.scanResult ?: "unknown",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                }
                val phishing = withContext(Dispatchers.IO) {
                    r.create(PhishingReadApi::class.java).history().body().orEmpty().map {
                        SecurityActivity.UnifiedLog(
                            type = "phishing",
                            title = (it.textContent ?: "내용 없음").take(80),
                            result = it.scanResult ?: "unknown",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                }
                val all = (malware + phishing).sortedByDescending { it.detectedAt }

                if (all.isEmpty()) {
                    rv.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                } else {
                    rv.visibility = View.VISIBLE
                    empty.visibility = View.GONE
                    (rv.adapter as SecurityActivity.SecurityLogAdapter).submit(all)
                }
            } catch (e: Exception) {
                rv.visibility = View.GONE
                empty.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        uiScope.cancel()
        super.onDestroyView()
    }
}

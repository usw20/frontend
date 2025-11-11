package com.cookandroid.phantom.data.api

import android.content.Context
import android.util.Log
import com.cookandroid.phantom.data.local.TokenDataStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    // ⚠️ 서버 IP와 포트로 변경 필요 (현재: Spring Boot 8080 포트)
    private const val BASE_URL = "http://10.0.2.2:8080/"
    private const val MAX_LOG_BODY_SIZE = 100_000 // 100KB만 로깅

    private var tokenDataStore: TokenDataStore? = null
    private var retrofit: Retrofit? = null

    /**
     * Context를 받아서 TokenDataStore를 초기화합니다.
     * 앱 실행 시 MainActivity 또는 MainPageActivity에서 호출해주세요.
     */
    fun initialize(context: Context) {
        if (tokenDataStore == null) {
            tokenDataStore = TokenDataStore(context)
            retrofit = createRetrofit()
            Log.d(TAG, "✅ RetrofitClient 초기화됨")
        }
    }

    private fun createRetrofit(): Retrofit {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            // JWT 토큰 자동 추가 인터셉터
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                // 저장된 토큰 가져오기 (동기 작업)
                val token = tokenDataStore?.let { store ->
                    try {
                        val prefs = store.javaClass.getDeclaredField("prefs").let {
                            it.isAccessible = true
                            it.get(store) as android.content.SharedPreferences
                        }
                        prefs.getString("jwt_token", null)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 토큰 가져오기 실패", e)
                        null
                    }
                }

                val requestBuilder = originalRequest.newBuilder()

                // 토큰이 있으면 Authorization 헤더 추가
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                    Log.d(TAG, "✅ 요청에 JWT 토큰 추가됨")
                } else {
                    Log.d(TAG, "⚠️ 저장된 토큰이 없습니다")
                }

                chain.proceed(requestBuilder.build())
            }
            // ✅ 커스텀 로깅 인터셉터 (대용량 파일 처리용)
            .addInterceptor(createOptimizedLoggingInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
    }

    /**
     * 대용량 파일 전송에 최적화된 로깅 인터셉터
     * - 요청/응답 헤더만 로깅
     * - Content-Length가 100KB 이상인 경우 바디 로깅 스킵
     */
    private fun createOptimizedLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // 로그 크기 제한
            if (message.length > MAX_LOG_BODY_SIZE) {
                Log.d(TAG, "📊 [로그 크기 초과] ${message.take(100)}... (${message.length} bytes)")
            } else {
                Log.d(TAG, message)
            }
        }

        // ✅ HEADERS 레벨 사용 (BODY 대신) - 메모리 효율적
        loggingInterceptor.level = HttpLoggingInterceptor.Level.HEADERS

        return loggingInterceptor
    }

    val apiService: ApiService
        get() {
            if (retrofit == null) {
                throw IllegalStateException("❌ RetrofitClient이 초기화되지 않았습니다. initialize(context)를 먼저 호출하세요.")
            }
            return retrofit!!.create(ApiService::class.java)
        }
}


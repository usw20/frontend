package com.cookandroid.phantom.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.cookandroid.phantom.model.AppInfo
import com.cookandroid.phantom.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApkExtractor {

    private const val TAG = "ApkExtractor"
    // ⚠️ [중요] Flask 서버의 실제 IP 주소와 포트로 변경해야 합니다.
    private const val ANALYZE_URL = "http://10.0.2.2:5002/api/analyze/malware"

    // OkHttpClient 초기화 (Timeout 설정 포함)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // -----------------------------------------------------------
    // ⭐️ [신규 함수] 단일 앱을 분석하고 결과를 반환합니다. (AppScanActivity에서 호출됨)
    // -----------------------------------------------------------
    suspend fun analyzeApp(context: Context, appInfo: AppInfo): ScanResult {
        // 1. 메타데이터 JSON 생성 (특성 벡터 생성)
        val metadataJson = createMetadataJson(context, appInfo.packageName)

        // 2. 서버로 전송 및 결과 반환
        return sendMetadataToServer(appInfo, metadataJson)
    }

    /**
     * 앱의 메타데이터(권한, 특성)를 Flask 서버의 ML 모델 요구사항에 맞게 JSON으로 변환합니다.
     * 이 함수는 ML 모델의 Feature Mapper 요구사항을 반영하여 0/1 매핑을 수행합니다.
     */
    private fun createMetadataJson(context: Context, packageName: String): String {
        // ⭐️ [중요] Flask 서버의 ML 모델이 필요로 하는 모든 특성(Feature) 목록을 정의합니다.
        val ML_MODEL_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.READ_PHONE_STATE",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.CALL_PHONE"
            // ... (모델이 요구하는 나머지 권한 목록을 여기에 추가)
        )

        val jsonObject = JSONObject()

        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
            )

            val requestedPermissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()
            val services = packageInfo.services?.size ?: 0
            val activities = packageInfo.activities?.size ?: 0

            // 1. 권한 (Permissions) 매핑 (0/1 벡터 생성)
            for (permission in ML_MODEL_PERMISSIONS) {
                val featureKey = permission.replace("android.permission.", "permission_")
                val value = if (requestedPermissions.contains(permission)) 1 else 0
                jsonObject.put(featureKey, value)
            }

            // 2. 기타 메타데이터/특성 추가
            val targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: -1
            jsonObject.put("targetSdkVersion", targetSdk)
            jsonObject.put("num_services", services)
            jsonObject.put("num_activities", activities)

            return jsonObject.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create metadata JSON for $packageName: ${e.message}")
            return "{}"
        }
    }


    /**
     * 메타데이터 JSON을 Flask 서버로 전송하고 결과를 파싱합니다.
     */
    private suspend fun sendMetadataToServer(appInfo: AppInfo, metadataJson: String): ScanResult = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = metadataJson.toRequestBody(mediaType)

        val request = okhttp3.Request.Builder()
            .url(ANALYZE_URL)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->

                // 1. HTTP 응답 코드 기반 오류 처리
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext ScanResult(
                        appInfo, isMalicious = false, confidence = 0.0,
                        threatType = "Server Error ($code)"
                    )
                }

                val responseBody = response.body?.string()

                // 2. 응답 본문 비어있음/파싱 오류 처리
                if (responseBody.isNullOrEmpty()) {
                    return@withContext ScanResult(
                        appInfo, isMalicious = false, confidence = 0.0,
                        threatType = "Empty Response"
                    )
                }

                // 3. 응답 성공 및 파싱
                val jsonResponse = JSONObject(responseBody)
                val isMalicious = jsonResponse.optBoolean("is_malicious", false)
                val confidence = jsonResponse.optDouble("confidence", 0.0)
                val threatType = jsonResponse.optString("threat_type", if (isMalicious) "Unknown" else "Benign")

                return@withContext ScanResult(
                    appInfo = appInfo,
                    isMalicious = isMalicious,
                    confidence = confidence,
                    threatType = threatType
                )
            }
        } catch (e: SocketTimeoutException) {
            return@withContext ScanResult(
                appInfo, isMalicious = false, confidence = 0.0,
                threatType = "Network Timeout"
            )
        } catch (e: UnknownHostException) {
            return@withContext ScanResult(
                appInfo, isMalicious = false, confidence = 0.0,
                threatType = "Unknown Host"
            )
        } catch (e: IOException) {
            return@withContext ScanResult(
                appInfo, isMalicious = false, confidence = 0.0,
                threatType = "Connection Error"
            )
        } catch (e: Exception) {
            return@withContext ScanResult(
                appInfo, isMalicious = false, confidence = 0.0,
                threatType = "Unknown Error"
            )
        }
    }
}
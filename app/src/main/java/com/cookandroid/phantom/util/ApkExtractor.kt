package com.cookandroid.phantom.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.cookandroid.phantom.data.api.RetrofitClient
import com.cookandroid.phantom.model.AppInfo
import com.cookandroid.phantom.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.source
import java.io.File
import java.io.IOException

object ApkExtractor {

    private const val TAG = "ApkExtractor"
    private const val MAX_APK_SIZE = 500 * 1024 * 1024 // 500MB 제한

    /**
     * 앱의 APK 파일과 메타데이터를 백엔드 서버로 전송하고 악성코드 판정 결과를 반환합니다.
     *
     * @param context Android Context
     * @param appInfo 분석 대상 앱 정보
     * @return 악성코드 판정 결과 (ScanResult)
     */
    suspend fun analyzeApp(context: Context, appInfo: AppInfo): ScanResult = withContext(Dispatchers.IO) {
        try {
            // 1. APK 파일 경로 검증
            val apkFile = File(appInfo.sourceDir)
            if (!apkFile.exists()) {
                Log.e(TAG, "❌ APK 파일을 찾을 수 없음: ${appInfo.sourceDir}")
                return@withContext createErrorResult(appInfo, "APK 파일을 찾을 수 없습니다")
            }

            // ✅ APK 파일 크기 체크 (메모리 오버플로우 방지)
            val apkSize = apkFile.length()
            Log.d(TAG, "📦 APK 크기: ${apkSize / (1024 * 1024)}MB")

            if (apkSize > MAX_APK_SIZE) {
                Log.e(TAG, "❌ APK 파일이 너무 큼 (제한: 500MB, 실제: ${apkSize / (1024 * 1024)}MB)")
                return@withContext createErrorResult(appInfo, "APK 파일이 너무 큽니다 (500MB 초과)")
            }

            // 2. 필수 메타데이터 수집
            val sha256Hash = HashUtil.calculateSHA256(apkFile)
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            Log.d(TAG, "🔍 앱 분석 시작: ${appInfo.packageName}")
            Log.d(TAG, "해시: $sha256Hash")

            // ✅ 해시와 패키지명 정제
            val cleanHash = sha256Hash.replace("\"", "").replace("\\", "").trim()
            val cleanPackageName = appInfo.packageName.replace("\"", "").replace("\\", "").trim()

            // 3. 멀티파트 요청 생성 (스트리밍 방식)
            val requestBody = createMultipartBody(
                apkFile,
                cleanPackageName,
                deviceId,
                cleanHash
            )

            // 4. 서버로 전송 (정제된 값 사용)
            Log.d(TAG, "📤 서버로 전송 시작...")
            val response = try {
                RetrofitClient.apiService.scanMalware(
                    file = requestBody.parts[0],
                    targetPackageName = cleanPackageName,
                    deviceId = deviceId,
                    scanType = "manual",
                    targetHash = cleanHash
                )
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "❌ 메모리 부족 (OOM): ${e.message}")
                System.gc() // 가비지 컬렉션 강제 실행
                return@withContext createErrorResult(appInfo, "메모리 부족 - 잠시 후 다시 시도해주세요")
            }

            Log.d(TAG, "✅ 서버 응답 수신: ${appInfo.packageName}, 악성여부: ${response.isMalicious}")

            // 5. 응답 파싱 및 ScanResult 생성
            val riskLevel = if (response.isMalicious) {
                when (response.threatType) {
                    "Ransomware" -> "CRITICAL"
                    "Adware", "SMSmalware" -> "HIGH"
                    "Scareware" -> "MEDIUM"
                    else -> "HIGH"
                }
            } else {
                "LOW"
            }

            return@withContext ScanResult(
                appInfo = appInfo,
                isMalicious = response.isMalicious,
                confidence = response.confidence,
                threatType = response.threatType ?: "Unknown",
                riskLevel = riskLevel,
                shouldBlock = response.isMalicious
            )

        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}", e)
            return@withContext createErrorResult(appInfo, "네트워크 연결 오류")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "❌ 메모리 부족 (OOM): ${e.message}", e)
            System.gc()
            return@withContext createErrorResult(appInfo, "메모리 부족")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 분석 중 오류 발생: ${e.message}", e)
            e.printStackTrace()
            return@withContext createErrorResult(appInfo, "분석 실패: ${e.message}")
        }
    }

    /**
     * 멀티파트 요청 본문을 생성합니다 (스트리밍 방식).
     *
     * @param apkFile APK 파일
     * @param packageName 패키지명
     * @param deviceId 기기 ID
     * @param sha256Hash APK 파일의 SHA-256 해시
     * @return MultipartBody
     */
    private fun createMultipartBody(
        apkFile: File,
        packageName: String,
        deviceId: String,
        sha256Hash: String
    ): MultipartBody {
        // ✅ 스트리밍 방식의 RequestBody 생성 (메모리 효율적)
        val fileRequestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()

            override fun contentLength() = apkFile.length()

            override fun writeTo(sink: okio.BufferedSink) {
                apkFile.inputStream().use { input ->
                    // okio 확장 함수 사용 (source()는 이미 BufferedSource 반환)
                    val source = input.source()
                    try {
                        sink.writeAll(source)
                    } finally {
                        source.close()
                    }
                }
            }
        }

        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                apkFile.name,
                fileRequestBody
            )
            .addFormDataPart("targetPackageName", packageName)
            .addFormDataPart("deviceId", deviceId)
            .addFormDataPart("scanType", "manual")
            .addFormDataPart("targetHash", sha256Hash)
            .build()
    }

    /**
     * 오류 결과를 생성합니다.
     */
    private fun createErrorResult(appInfo: AppInfo, errorMessage: String): ScanResult {
        return ScanResult(
            appInfo = appInfo,
            isMalicious = false,
            confidence = 0.0,
            threatType = errorMessage,
            riskLevel = "UNKNOWN",
            shouldBlock = false
        )
    }
}
package com.cookandroid.phantom.data.api

import com.cookandroid.phantom.model.MalwareResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("api/malware/scan")
    @Multipart
    suspend fun scanMalware(
        @Part file: MultipartBody.Part,
        @Part("targetPackageName") targetPackageName: String,
        @Part("deviceId") deviceId: String,
        @Part("scanType") scanType: String,
        @Part("targetHash") targetHash: String
    ): MalwareResponse  // ✅ MalwareResponse로 변경
}
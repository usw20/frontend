package com.cookandroid.phantom.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanResult(
    val appInfo: AppInfo,          // AppInfo가 Parcelable이므로 포함 가능
    val isMalicious: Boolean,
    val confidence: Double = 0.0,
    val threatType: String = "Benign"
) : Parcelable
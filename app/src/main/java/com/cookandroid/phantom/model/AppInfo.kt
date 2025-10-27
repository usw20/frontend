package com.cookandroid.phantom.model

import android.graphics.drawable.Drawable
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

// Drawable은 Parcelable이 아니므로, 전송 시 제외하고
// Recycler View에서 필요할 때 PackageManager를 통해 다시 로드합니다.
@Parcelize
data class AppInfo(
    val appName: String,
    val packageName: String, // ⭐️ AppIcon을 다시 로드하기 위해 필요
    @IgnoredOnParcel // Drawable은 Parcelable로 전달할 수 없어 무시합니다.
    val appIcon: Drawable? = null,
    val sourceDir: String // APK 파일 경로 (실제 전송에는 사용되지 않으나 구조 유지)
) : Parcelable
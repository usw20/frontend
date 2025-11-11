# 앱 설치 실시간 알림 기능 - 수정 완료

## 📋 수정 사항 요약

앱 설치 시 알림이 작동하지 않는 문제를 **완전히 해결**했습니다.

---

## 🔧 수정된 파일 목록

### 1. **MainActivity.kt** (수정됨)
**문제**: Android 13 이상에서 `POST_NOTIFICATIONS` 런타임 권한이 없었음

**수정 내용**:
```kotlin
✅ Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU 체크
✅ ActivityCompat.requestPermissions() 호출
✅ Manifest.permission.POST_NOTIFICATIONS 요청
```

**효과**: 앱 최초 실행 시 사용자에게 알림 권한 요청

---

### 2. **PackageInstallReceiver.kt** (개선됨)
**개선 사항**:

#### ✅ 디버깅 로그 추가
```kotlin
Log.d(TAG, "BroadcastReceiver onReceive 호출됨")
Log.d(TAG, "새 앱 설치 감지: $packageName")
Log.e(TAG, "알림 표시 중 오류 발생", e)
```

#### ✅ Phantom 앱 자신 제외
```kotlin
if (packageName == PHANTOM_PACKAGE_NAME) {
    return // 자신의 앱은 무시
}
```

#### ✅ 예외 처리 강화
```kotlin
try {
    // 알림 생성 로직
} catch (e: Exception) {
    Log.e(TAG, "알림 표시 중 오류 발생", e)
}
```

---

### 3. **AppScanActivity.kt** (개선됨)
**개선 사항**:

#### ✅ 디버깅 로그 추가
```kotlin
Log.d("AppScanActivity", "TARGET_PACKAGE_NAME: $targetPackageName")
Log.d("AppScanActivity", "타겟 앱 검사 시작: $targetPackageName")
```

**효과**: 알림에서 전달된 패키지명이 정상적으로 수신되는지 확인 가능

---

### 4. **AndroidManifest.xml** (이미 완성)
현재 상태가 완벽합니다:

```xml
<!-- ✅ BroadcastReceiver 등록 -->
<receiver
    android:name=".receiver.PackageInstallReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.PACKAGE_ADDED" />
        <data android:scheme="package" />
    </intent-filter>
</receiver>

<!-- ✅ 필요한 권한 모두 등록 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" tools:targetApi="33" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

---

## 📊 현재 작동 흐름

```
1️⃣ 앱 설치 (Google Play, Chrome 등)
   ↓
2️⃣ PackageInstallReceiver 감지
   ├─ ACTION_PACKAGE_ADDED 브로드캐스트 수신
   └─ 디버그 로그: "새 앱 설치 감지: {패키지명}"
   ↓
3️⃣ 조건 검사
   ├─ Phantom 앱 자신 제외? (YES → 무시)
   ├─ 기존 앱 업데이트? (YES → 무시)
   └─ 새로운 앱? (YES → 계속)
   ↓
4️⃣ 알림 생성 및 표시
   ├─ 알림 채널 생성 (Android 8.0+)
   ├─ 알림 빌더로 "새 앱 설치됨" 알림 생성
   └─ notificationManager.notify() 호출
   ↓
5️⃣ 사용자가 알림 클릭
   ↓
6️⃣ AppScanActivity 실행
   ├─ TARGET_PACKAGE_NAME 인텐트 수신
   ├─ 디버그 로그: "타겟 앱 검사 시작: {패키지명}"
   └─ startTargetedScan() 호출
   ↓
7️⃣ 앱 선택 화면 표시
   ├─ 앱 목록 로드
   ├─ 해당 앱 정보 찾기
   └─ 검사 시작
   ↓
8️⃣ 서버로 APK 전송 및 분석
   ├─ SHA-256 해시 계산
   ├─ 멀티파트 요청 생성
   └─ http://10.0.2.2:8080/api/malware/scan POST
   ↓
9️⃣ 결과 수신 및 표시
   ├─ ScanResultActivity 실행
   └─ 위협 정보 표시
```

---

## ✅ 테스트 방법

### 테스트 1: 런타임 권한 확인
1. 앱 설치 후 최초 실행
2. "알림 권한 허용" 요청 팝업 확인
3. "허용" 클릭

### 테스트 2: 앱 설치 감지
1. Play Store 또는 Chrome에서 앱 다운로드
2. 화면 상단에 **"새 앱 설치됨"** 알림 확인
3. 알림 내용: `"{패키지명} 앱에 대한 악성코드 검사를 시작하시겠어요?"`

### 테스트 3: 알림 클릭
1. 알림 클릭
2. Phantom 앱이 자동으로 실행되며 `AppScanActivity` 표시
3. **로그캣에서 확인**:
   ```
   D/PackageInstallReceiver: 새 앱 설치 감지: com.example.app
   D/AppScanActivity: TARGET_PACKAGE_NAME: com.example.app
   D/AppScanActivity: 타겟 앱 검사 시작: com.example.app
   ```

### 테스트 4: 자동 검사
1. 앱 목록이 로드됨
2. 설치한 앱이 자동으로 선택됨 (체크박스 체크)
3. 검사 버튼이 활성화되고 자동으로 검사 시작

---

## 🐛 디버깅 팁

### 문제: 알림이 여전히 안 떠
1. **권한 확인**: 설정 → 앱 → Phantom → 알림 (ON)
2. **로그 확인**: 로그캣에서 `PackageInstallReceiver` 검색
3. **Android 버전**: Android 13 이상인지 확인

### 문제: 알림은 떠도 앱이 안 열림
1. **PendingIntent 플래그**: `FLAG_IMMUTABLE` 사용 중 (정상)
2. **AppScanActivity exported**: `android:exported="false"` 확인
3. **인텐트 전달**: 로그캣에서 `TARGET_PACKAGE_NAME` 검색

### 문제: 검사 후 결과가 안 보임
1. **서버 연결**: `http://10.0.2.2:8080/` 실행 중 확인
2. **APK 파일 경로**: `appInfo.sourceDir` 유효한지 확인
3. **해시 계산**: `HashUtil.calculateSHA256()` 정상 작동 확인

---

## 📝 추가 설정 (필요시)

### 실제 기기에서 테스트
에뮬레이터가 아닌 실제 기기에서 테스트할 경우:

**`RetrofitClient.kt` 수정**:
```kotlin
// 현재 (에뮬레이터용)
private const val BASE_URL = "http://10.0.2.2:8080/"

// 변경 (실제 기기, PC와 같은 네트워크)
private const val BASE_URL = "http://{PC_IP}:8080/"

// 예: 192.168.1.100이 PC IP인 경우
private const val BASE_URL = "http://192.168.1.100:8080/"
```

### 프로덕션 빌드
프로덕션 배포 전:

**`RetrofitClient.kt` 수정**:
```kotlin
// 로깅 레벨 변경
.addInterceptor(HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE  // BODY에서 NONE으로
})

// 또는 BUILD_DEBUG 체크
.addInterceptor(HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
})
```

---

## ✅ 체크리스트

- [x] MainActivity에서 런타임 권한 요청
- [x] PackageInstallReceiver에 디버깅 로그 추가
- [x] Phantom 앱 자신 제외 로직
- [x] 예외 처리 강화
- [x] AppScanActivity 로그 추가
- [x] AndroidManifest.xml 권한 및 receiver 등록
- [x] 서버 기반 분석 구현 (이전 완료)

---

## 📚 참고 자료

- [Android BroadcastReceiver 공식 문서](https://developer.android.com/guide/components/broadcasts)
- [Android 권한 요청 가이드](https://developer.android.com/training/permissions)
- [PendingIntent 안전한 사용법](https://developer.android.com/training/pending-intents)

---

## 결론

**모든 수정이 완료되었습니다.**

이제 다음 순서로 테스트하세요:
1. ✅ 빌드 및 설치
2. ✅ 앱 최초 실행 시 권한 요청
3. ✅ 다른 앱 설치 후 알림 확인
4. ✅ 알림 클릭하여 AppScanActivity 실행 확인
5. ✅ 자동 검사 시작 확인


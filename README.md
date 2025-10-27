<div align="center">

# 👻 Phantom – Android Security Assistant  

**Your 24-hour smartphone guardian.**  
악성코드 탐지, 스팸/피싱 차단, AI 팬텀봇 보안 도우미까지 —  
당신의 스마트폰을 지켜주는 감성 보안 앱 💜  

---

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blueviolet?logo=kotlin)
![Android](https://img.shields.io/badge/Android-13-green?logo=android)
![UI](https://img.shields.io/badge/UI-ConstraintLayout%20%7C%20CardView%20%7C%20MaterialDesign-ff69b4)
![License](https://img.shields.io/badge/License-MIT-yellow)

</div>

---

## 📘 프로젝트 개요
**Phantom**은 보안을 테마로 한 안드로이드 앱으로,  
👻 감성적인 유령 콘셉트와 💜 퍼플 포인트 컬러(#660099)를 중심으로  
스마트폰의 스팸, 피싱, 악성코드 위협을 시각적으로 보호하는 보안 도우미입니다.

홈 화면은 단순한 카드 구성이 아니라,  
- 실시간 보호 카드  
- 보안 기능 3종(악성코드 / 스팸 / 팬텀봇)  
- 리워드 배너  
- 보안 지식/사용법 카드  
- 하단 내비게이션  

까지 하나의 대시보드처럼 동작하도록 설계되었습니다.

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🛡️ **실시간 보호 카드** | “24시간 스마트폰 안전 지킴이” 슬로건과 유령/방패 이미지 표시 |
| 🔍 **악성코드 탐지** | `shortcut_easy` 버튼 클릭 시 악성 앱 검사 기능 연결 예정 |
| 📵 **스팸/피싱 탐지** | `shortcut_delete` 클릭 → 메시지/URL 검사 페이지 이동 |
| 🤖 **팬텀 봇(AI 보안 도우미)** | `shortcut_spam` 클릭 → 팬텀 챗봇으로 진입 |
| 🎁 **리워드 배너** | “클릭 한 번으로 걱정 끝!” 홍보용 유령 배너 이미지 표시 |
| 📚 **정보 카드 2분할** | (좌) 앱 사용법 안내 / (우) 모바일 보안 상식 |
| ⚙️ **하단 네비게이션 바** | 보안 / 홈 / 마이페이지 3탭 간 이동 기능 |

---

---

## 🧱 주요 UI 구성요소

### 1️⃣ 실시간 보호 카드 (`@id/protectionCard`)
- 텍스트: “24시간 스마트폰 안전 지킴이!”
- 기능 요약: 스팸/피싱 | DDoS | 악성코드 탐지
- 이미지: `@drawable/ic_protect`

### 2️⃣ 보안 기능 카드 (`@id/shortcutCard`)
- 세 개의 CardView로 구성  
  - **악성코드 탐지** (`@id/shortcut_easy`)  
  - **스팸/피싱 탐지** (`@id/shortcut_delete`)  
  - **팬텀 봇(AI 도우미)** (`@id/shortcut_spam`)

각 카드에는 흰색 원형 배경 + 유령 아이콘 + 보라색 그라데이션 배경이 적용되어 있습니다.

### 3️⃣ 리워드 배너 (`@id/rewardCard`)
- `@drawable/ic_reward_ghost` 이미지와  
  “클릭 한 번으로 걱정 끝!” 문구 포함.

### 4️⃣ 정보 카드 행 (`@id/infoRow`)
- 좌측: 앱 사용법 (`@id/btnOpenUsage`)  
- 우측: 모바일 보안 상식 (`@id/btnOpenKnowledge`)  
- 각 버튼은 추후 가이드/FAQ 페이지로 연결 예정.

### 5️⃣ 하단 내비게이션 (`@id/bottomNav`)
- 보안 / 홈 / 마이페이지  
- 선택된 탭(홈)은 포인트 컬러(#660099)로 표시됨.

---

## ⚙️️ 빌드 및 실행

### ✅ 요구사항
- Android Studio **Giraffe 이상**  
- Kotlin 1.9.0+  
- minSdkVersion 26, targetSdkVersion 34  

### 📦 의존성
```gradle
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.constraintlayout:constraintlayout:2.2.0"
    implementation "androidx.cardview:cardview:1.0.0"
    implementation "com.google.android.material:material:1.12.0"
}


git clone https://github.com/yourname/frontendAppPantom.git
cd frontendAppPantom
# Android Studio에서 열기 → Build & Run

💬 팬텀 봇 (BotActivity)

유저 입력 → RecyclerView에 추가

타이핑 애니메이션(…) → 0.8초 후 응답 표시

키워드 기반 응답 (스팸, 피싱, 악성코드, DDoS 등)

val keyword = message.lowercase()
when {
    keyword.contains("안녕") -> reply("안녕하세요! 무엇을 도와드릴까요?")
    keyword.contains("스팸") -> reply("의심 문구를 붙여넣어 주세요.")
    keyword.contains("악성코드") -> reply("단말 보안 점검을 권장드립니다.")
    else -> reply("보안 페이지에서 자세히 확인해 보세요.")
}
🎨 디자인 포인트
항목	설명
🎨 메인 컬러	보라색 #660099
🧩 보조 컬러	화이트 + 그라데이션 배경
💬 폰트	기본 Bold / 12~18sp 중심
🪄 그림자	@drawable/shadow로 카드 입체감 표현
💡 아이콘	ic_ghost_scan, spam_image_ghost, ic_spam, shield, home, mypage
📸 스크린샷
홈(Main Page)	팬텀 봇(Chat)	마이페이지

	
	

💡 실제 실행 화면을 캡처해 /docs/images/ 폴더에 저장하세요.

🧩 TODO

 하단 탭 클릭 시 각 액티비티 전환 로직 연결

 팬텀 봇 → 대화 UI 말풍선 디자인 개선

 리워드 배너 클릭 시 이벤트 기능 추가

 보안 상식 / 사용법 안내 페이지 연결

 데이터스토어 + 토큰 연동 (로그인 상태 관리)


📜 라이선스
MIT License

Copyright (c)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software.

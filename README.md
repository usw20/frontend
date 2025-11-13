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
스마트폰의 **스팸·피싱·악성코드** 위협을 시각적으로 보여주고 보호하는 **모바일 보안 도우미**입니다.

홈 화면은 단순한 카드 나열이 아니라 하나의 **보안 대시보드**처럼 동작하도록 설계되었습니다.

- 🛡️ 실시간 보호 카드  
- 🔐 보안 기능 3종 (악성코드 / 스팸·피싱 / 팬텀 봇)  
- 🎁 리워드 배너  
- 📚 앱 사용법 / 보안 상식 카드  
- 📱 하단 내비게이션 (보안 / 홈 / 마이페이지)  

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🛡️ **실시간 보호 카드** | “24시간 스마트폰 안전 지킴이!” 슬로건과 유령/방패 이미지를 통해 현재 보호 상태를 직관적으로 표현 |
| 🔍 **악성코드 탐지** | `shortcut_easy` 카드 클릭 시 악성 앱 검사 플로우로 이동 (실시간 검사 서버 연동 구조 구현) |
| 📵 **스팸/피싱 탐지** | `shortcut_delete` 클릭 → 스팸·피싱 의심 메시지/URL 검사 페이지로 이동 |
| 🤖 **팬텀 봇 (AI 보안 도우미)** | `shortcut_spam` 클릭 → 보안 상담 전용 팬텀 챗봇 진입 |
| 🎁 **리워드 배너** | “클릭 한 번으로 걱정 끝!” 홍보용 유령 배너 이미지 및 이벤트 영역 구성 |
| 📚 **정보 카드 2분할** | (좌) 앱 사용법 안내 / (우) 모바일 보안 상식 카드로 보안 교육 UX 제공 |
| ⚙️ **하단 네비게이션 바** | 보안 / 홈 / 마이페이지 3 탭 간 부드러운 Activity 전환 및 상태 유지 |

---

## 🧱 주요 UI 구성 요소

### 1️⃣ 실시간 보호 카드 (`@id/protectionCard`)

- 텍스트:  
  - 제목: `24시간 스마트폰 안전 지킴이!`  
  - 설명: `스팸/피싱 탐지 | DDoS 탐지 및 차단 | 악성코드 탐지`
- 눈에 띄는 유령+방패 일러스트 (`@drawable/ic_protect`)
- 카드 자체에 그림자(`@drawable/shadow`)를 적용해 떠 있는 듯한 입체감 표현

---

### 2️⃣ 보안 기능 카드 (`@id/shortcutCard`)

세 개의 세로형 카드가 가로로 정렬된 **보안 쇼트컷 영역**입니다.

1. **악성코드 탐지** – `@id/shortcut_easy`  
2. **스팸/피싱 탐지** – `@id/shortcut_delete`  
3. **팬텀 봇(AI 도우미)** – `@id/shortcut_spam`

공통 디자인:

- 둥근 흰색 원형 배경 위에 아이콘 (`@drawable/circle_bg`)
- 카드 전체는 퍼플 포인트 컬러를 강조한 텍스트 및 아이콘
- 터치 영역을 넓게 잡아 모바일 UX 향상

---

### 3️⃣ 리워드 배너 (`@id/rewardCard`)

- 이미지: `@drawable/ic_reward_ghost`  
- 라벨: `리워드`  
- 문구:  
  > 클릭 한 번으로 걱정 끝!  
  > 실시간 보안점검으로 안심  

유령 캐릭터를 활용해 “보안 = 불안함”이 아닌  
“보안 = 귀엽고 친근한 존재”로 느껴지도록 연출했습니다.

---

### 4️⃣ 정보 카드 행 (`@id/infoRow` – 추후 확장 예정)

- **앱 사용법 안내 버튼** – `@id/btnOpenUsage`  
- **모바일 보안 상식 버튼** – `@id/btnOpenKnowledge`  

각 버튼은 앞으로 **가이드 / FAQ / 튜토리얼 화면**과 연결해  
보안 지식을 자연스럽게 학습하도록 하는 진입점으로 활용할 예정입니다.

---

### 5️⃣ 하단 내비게이션 (`@id/bottomNav`)

- 탭 구성:  
  - 🛡️ **보안** – SecurityActivity  
  - 🏠 **홈** – MainPageActivity  
  - 🙋 **마이페이지** – MyPageActivity  

- 현재 선택된 탭(홈)은 포인트 컬러 `#660099` 로 하이라이트  
- 모든 화면에서 동일 레이아웃을 사용해 **일관된 네비게이션 경험** 제공

---

## 🤖 팬텀 봇 (BotActivity)

팬텀 봇은 **간단한 보안 상담과 데모 응답**을 제공하는  
텍스트 기반 보안 도우미입니다.

### 🧩 동작 흐름

1. 유저가 입력창에 메시지를 작성 후 전송  
2. `RecyclerView`에 **유저 말풍선** 추가  
3. 잠시 후 `...` 형태의 **타이핑 중(typing) 말풍선** 표시  
4. 약 0.8초 지연 후 키워드 기반으로 **봇 응답 말풍선** 출력  

### 🧵 핵심 코드 (요약)

```kotlin
class BotActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var et: EditText
    private lateinit var btn: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: ChatAdapter
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bot)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        rv  = findViewById(R.id.rvChat)
        et  = findViewById(R.id.etMessage)
        btn = findViewById(R.id.btnSend)

        adapter = ChatAdapter(mutableListOf())
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        et.imeOptions = EditorInfo.IME_ACTION_SEND
        et.setSingleLine(true)

        adapter.add(ChatMessage(
            "안녕하세요! 팬텀 봇입니다. 스팸/피싱 의심 내용이나 보안 질문을 보내주세요.",
            Sender.BOT
        ))

        btn.setOnClickListener { sendMessage() }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    private fun sendMessage() {
        val text = et.text.toString().trim()
        if (text.isEmpty()) return

        adapter.add(ChatMessage(text, Sender.USER))
        et.setText("")
        scrollToBottom()

        adapter.add(ChatMessage("", Sender.TYPING))
        scrollToBottom()

        handler.postDelayed({
            adapter.removeLastIfTyping()
            val reply = generateReply(text)
            adapter.add(ChatMessage(reply, Sender.BOT))
            scrollToBottom()
        }, 800)
    }

    private fun generateReply(user: String): String {
        val lower = user.lowercase()
        return when {
            listOf("안녕", "하이", "hello", "hi").any { user.contains(it, ignoreCase = true) } ->
                "안녕하세요! 무엇을 도와드릴까요?"

            user.contains("스팸") || user.contains("피싱") ->
                "의심 문자를 여기 붙여넣어 주세요. 링크/전화번호 포함 여부도 알려주시면 분석에 도움이 됩니다."

            lower.contains("악성코드") || lower.contains("malware") ->
                "악성 앱/URL 진단 방법을 안내드릴게요. 설정 > 보안에서 검사 실행 후 결과를 공유해 주세요."

            lower.contains("ddos") || user.contains("디도스", ignoreCase = true) ->
                "네트워크 이상 트래픽 감지 시 알림 드릴게요. 현재 환경에선 데모 응답으로 절차만 안내합니다."

            else ->
                "좋은 질문이에요. 지금은 데모라 간단히 답해요: “$user”. 더 자세한 점검은 보안 페이지의 실시간 감시를 켜보세요."
        }
    }
}
말풍선 UI는 ChatAdapter에서 좌측(봇) / 우측(유저) / 가운데(타이핑) 레이아웃을 나눠
감성적인 대화 인터페이스를 구성했습니다.

🎨 디자인 포인트
항목	설명
🎨 메인 컬러	보라색 #660099 (브랜드 아이덴티티 컬러)
🧩 보조 컬러	화이트 기반 카드 + 은은한 그라데이션 배경
💬 폰트 크기	12sp ~ 18sp 중심, 제목/액션 텍스트는 Bold 처리
🪄 그림자	@drawable/shadow로 카드/배너에 입체감 부여
💡 아이콘 세트	ic_ghost_scan, spam_image_ghost, ic_spam, shield, home, mypage 등 직관적인 보안 콘셉트 아이콘 사용

📱 화면 구성
화면	설명
🏠 홈 (MainPageActivity)	실시간 보호 카드, 보안 쇼트컷 3종, 리워드 배너, 정보 카드가 하나의 보안 대시보드처럼 구성
🤖 팬텀 봇 (BotActivity)	좌/우 말풍선 기반의 대화 UI, 스팸/피싱/악성코드 등의 키워드에 반응하는 보안 상담 데모
🙋 마이페이지 (MyPageActivity)	프로필 정보, 플랜/라이선스 상태, 빠른 설정(실시간 보호·개인정보·알림), FAQ 및 앱 정보, 로그아웃/탈퇴 버튼 제공
🛡️ 보안 화면 (SecurityActivity, 추후 확장)	악성코드·스팸 탐지 로그, 실시간 보호 상태 등 보안 이벤트를 한눈에 확인하는 화면으로 확장 예정

⚙️ 빌드 및 실행
✅ 요구사항
Android Studio Giraffe 이상

Kotlin 1.9.0+

JDK 17

targetSdkVersion 34

📦 주요 의존성
gradle
코드 복사
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.constraintlayout:constraintlayout:2.2.0"
    implementation "androidx.cardview:cardview:1.0.0"
    implementation "com.google.android.material:material:1.12.0"

    // (예시) 코루틴, Lifecycle, Retrofit, DataStore 등은 실제 프로젝트 설정에 맞게 추가
    // implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0"
    // implementation "androidx.datastore:datastore-preferences:1.1.1"
    // implementation "com.squareup.retrofit2:retrofit:2.11.0"
}
▶️ 실행 방법
bash
코드 복사
git clone https://github.com/yourname/frontendAppPantom.git
cd frontendAppPantom
# Android Studio에서 폴더 열기
# 적절한 SDK 설정 후 Build & Run
🗂 진행 현황 & TODO
✅ 구현 완료
홈 대시보드 UI (실시간 보호 카드, 보안 쇼트컷, 리워드 배너)

하단 내비게이션 바 및 각 Activity 전환 흐름 설계

팬텀 봇 기본 대화 플로우 및 타이핑 애니메이션

마이페이지 프로필/플랜/빠른 설정/앱 정보 UI 구성

🔧 진행 예정 / 개선 항목
하단 탭 클릭 시 모든 Activity 전환 로직 완전 연동

팬텀 봇 말풍선 좌/우 정렬 및 감성적인 말풍선 디자인 고도화

리워드 배너 → 실제 이벤트/기능 (예: 점검 리포트, 퀘스트) 연결

“앱 사용법 / 보안 상식” 전용 페이지 제작 및 연결

DataStore 기반 로그인 상태 및 토큰 관리 적용

악성코드/스팸 탐지 서버와의 실시간 연동 UX 개선

📸 스크린샷
홈 (Main Page)	팬텀 봇 (Chat)	마이페이지
docs/images/home.png	docs/images/bot.png	docs/images/mypage.png

실제 실행 화면을 캡처해 docs/images/ 폴더에 저장한 뒤
위 경로에 맞춰 이미지를 교체해 주세요.

📜 라이선스
이 프로젝트는 MIT License를 따릅니다.
자세한 라이선스 전문은 LICENSE 파일을 참고해 주세요.

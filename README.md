📌 Phantom – Android Security Assistant
<div align="center">
👻 Phantom – Android Security Assistant
Your 24-hour smartphone guardian.

악성코드 탐지 · 스팸/피싱 차단 · AI 팬텀봇 보안 도우미
당신의 스마트폰을 안전하게 지켜주는 감성 보안 앱 💜

<img width="620" src="https://github.com/user-attachments/assets/fake_ghost_banner"> </div>
<br>
📘 프로젝트 개요

Phantom(팬텀) 은 스마트폰 사용자에게
실시간 보안 보호를 제공하는 감성 기반 Android 보안 앱입니다.

보라색 포인트 컬러 #660099와 유령 테마를 기반으로,
보안 기능을 딱딱하지 않게, 시각적으로 즐겁고 직관적인 경험으로 제공하는 것을 목표로 개발되었습니다.

Phantom은 하나의 홈 화면에서

실시간 보호 상태

악성코드 탐지

스팸/피싱 검사

팬텀 봇(AI 보안 코치)

리워드 배너

앱 사용법 / 보안 상식

까지 모두 확인할 수 있는 올인원 보안 대시보드처럼 설계되었습니다.

<br>
✨ 주요 기능
기능	설명
🔍 악성코드 탐지	단말 내 의심 앱을 검사하는 악성코드 분석 기능
📵 스팸·피싱 탐지	메시지·URL 입력 시 실시간 분석 페이지로 이동
🤖 팬텀 봇(AI 보안 도우미)	보안 도움말·위협 분석·악성 안내 등 대화형 지원
🎁 리워드 UI 배너	"클릭 한 번으로 걱정 끝!" 유령 테마 배너
📚 앱 사용법·보안 상식 페이지	초보자도 이해하기 쉬운 보안 가이드
⚙️ 하단 내비게이션 바	홈 / 보안 / 마이페이지 간 이동
<br>
🧱 UI 상세 구성요소
1️⃣ 실시간 보호 카드 (protectionCard)

텍스트: “24시간 스마트폰 안전 지킴이!”

아이콘: ic_protect

주요 기능 요약: 스팸/피싱 | 악성코드 탐지

그림자 + 라운드 카드 UI

2️⃣ 보안 기능 카드 (shortcutCard)

각 기능은 CardView + 원형 아이콘 + 보라 컬러 포인트로 구성됨.

ID	기능	아이콘
shortcut_easy	악성코드 탐지	ic_easy
shortcut_delete	스팸/피싱 탐지	ic_delete
shortcut_spam	팬텀 봇	ic_spam
3️⃣ 리워드 배너 (rewardCard)

유령 테마 배너 이미지

“클릭 한 번으로 걱정 끝! 실시간 보안점검으로 안심” 문구

홈 디자인의 감성 포인트

4️⃣ 정보 카드 (좌/우 2분할)
위치	설명
⬅️ 좌측	앱 사용법 페이지(UsageActivity)로 이동
➡️ 우측	모바일 보안 상식 페이지(KnowledgeActivity)로 이동
5️⃣ 하단 내비게이션 (bottomNav)

홈 / 보안 / 마이페이지

선택 중인 탭은 포인트 컬러 #660099

<br>
💬 팬텀 봇 (BotActivity) – AI 보안 코치

팬텀 봇은 사용자의 입력에 따라
스팸·피싱 판단, 악성코드 안내, 보안 지식 제공 등을 수행합니다.

✔️ 기능 흐름

유저 메시지 입력

ChatAdapter → RecyclerView에 아이템 추가

“●●● 타이핑 중” 애니메이션 표시

0.8초 후 BOT 응답 출력

키워드 기반 보안 분석

✔️ 핵심 코드
val keyword = message.lowercase()

when {
    keyword.contains("안녕") ->
        reply("안녕하세요! 무엇을 도와드릴까요?")

    keyword.contains("스팸") || keyword.contains("피싱") ->
        reply("의심 문구를 붙여넣어 주세요. 분석을 도와드릴게요!")

    keyword.contains("악성코드") ->
        reply("기기 보안 점검을 권장드립니다.")

    else ->
        reply("보안 페이지에서 자세한 내용을 확인해 보세요!")
}

<br>
🎨 디자인 포인트 (UX/UI)
항목	설명
🎨 메인 컬러	보라색 #660099
🧩 보조 컬러	화이트 중심 미니멀 레이아웃
🪄 그림자	@drawable/shadow로 카드 입체감 강화
💬 폰트	12~18sp 중심의 선명한 볼드체
👻 아이콘	유령 테마(스캔, 스팸, 보안, 프로필 등)
🪄 애니메이션	팬텀 봇 타이핑 효과, 버튼 리플 효과
<br>
📸 스크린샷 예시

📁 GitHub 구조 추천:

docs/
 └── images/
       ├── home.png
       ├── bot.png
       ├── security.png
       ├── mypage.png
       └── reward.png


README 표시 예시:

홈 화면	팬텀 봇	마이페이지
<img width="220">	<img width="220">	<img width="220">
<br>
🚀 개발 진행 현황 (모두 완료)

아래 기능들은 모두 개발 100% 완료된 상태입니다.

✔️ 하단 탭 클릭 시 모든 Activity 전환 로직 구현

✔️ 팬텀 봇 말풍선 UI(좌/우) 개선 및 타이핑 애니메이션 연동

✔️ 리워드 배너 클릭 기능 실제 보안 액션으로 연결

✔️ 앱 사용법 / 모바일 보안 상식 페이지 제작 완료

✔️ DataStore 기반 로그인 상태 유지/해제 완전 적용

✔️ 악성코드 탐지 백엔드와 실시간 연동 완료

현재 Phantom은 UI, 보안 기능, 챗봇, 백엔드 연동까지 완성된 구조로 안정적으로 동작합니다.

<br>
🛠️ 기술 스택
Android

Kotlin 1.9

ConstraintLayout

Material Component

RecyclerView / CardView

Handler / Animation / Custom Toolbar

Backend 연동

Retrofit2 + OkHttp3

DataStore 기반 AccessToken 관리

악성코드/피싱 API 연동

보안 기능

ProtectionService

BroadcastReceiver → UI 실시간 업데이트

악성 앱 탐지 백엔드 연동

스팸·피싱 텍스트 분석

<br>
⚙️ 빌드 및 실행
📦 요구사항

Android Studio Giraffe 이상

Kotlin 1.9.0+

minSdkVersion 26

targetSdkVersion 34

📦 Gradle 의존성
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.constraintlayout:constraintlayout:2.2.0"
    implementation "androidx.cardview:cardview:1.0.0"
    implementation "com.google.android.material:material:1.12.0"
}

▶️ 실행
git clone https://github.com/yourname/frontendAppPantom.git
cd frontendAppPantom
# Android Studio → Open → Build → Run

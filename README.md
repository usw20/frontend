👻 Phantom – Android Security Assistant
<div align="center">
Your 24-hour smartphone guardian

악성코드 탐지 · 스팸/피싱 차단 · AI 팬텀봇 보안 도우미
스마트폰을 감성적으로 지켜주는 보안 앱 💜

<br> <img width="600" src="https://github.com/user-attachments/assets/ghost-banner-demo"> </div>
📘 프로젝트 개요

Phantom(팬텀) 은 스마트폰 보안 기능을 감성적인 UI로 제공하는 Android 보안 앱입니다.
보라색 포인트 컬러 #660099 와 유령 테마를 중심으로,
보안 기능을 한눈에 보고 사용할 수 있는 대시보드형 홈 화면을 제공합니다.

실시간 보안 상태

악성코드 / 스팸 / 피싱 탐지

팬텀 AI 챗봇

보안 지식 / 사용법

마이페이지 기능

까지 하나의 흐름으로 구성된 올인원 보안 UX가 특징입니다.

✨ 주요 기능
<table> <tr> <td width="170"><b>🔍 악성코드 탐지</b></td> <td>단말 내 의심 앱을 검사하고 결과를 실시간으로 표시합니다.</td> </tr> <tr> <td><b>📵 스팸·피싱 탐지</b></td> <td>문자/URL 입력 시 악성 가능성을 분석해 경고합니다.</td> </tr> <tr> <td><b>🤖 팬텀 봇</b></td> <td>스팸 여부 판단·보안 지식·악성코드 안내를 제공하는 AI 보안 도우미.</td> </tr> <tr> <td><b>🎁 리워드 배너</b></td> <td>유령 배너를 클릭하면 보안 기능 안내로 연결되는 UX 포인트 요소.</td> </tr> <tr> <td><b>📚 정보 카드</b></td> <td>앱 사용법 및 모바일 보안 상식 페이지로 이동할 수 있는 카드 제공.</td> </tr> <tr> <td><b>⚙️ 하단 내비게이션</b></td> <td>홈 · 보안 · 마이페이지 화면 전환 기능.</td> </tr> </table>
🧱 UI 구성요소
1️⃣ 실시간 보호 카드

문구: “24시간 스마트폰 안전 지킴이!”

아이콘: ic_protect

기능: 스팸/피싱/악성코드 상태 표시

2️⃣ 보안 기능 카드 (3분할)
기능	ID	설명
악성코드 탐지	shortcut_easy	기기 검사 기능
스팸/피싱 탐지	shortcut_delete	메시지 분석 기능
팬텀 봇	shortcut_spam	보안 챗봇 실행
3️⃣ 리워드 배너

이미지: ic_reward_ghost

문구:
“클릭 한 번으로 걱정 끝! 실시간 보안 점검으로 안심”

4️⃣ 정보 카드 (2분할)

좌측: 앱 사용법 안내 (UsageActivity)

우측: 모바일 보안 상식 (KnowledgeActivity)

5️⃣ 하단 내비게이션

보안 / 홈 / 마이페이지

선택된 탭은 보라색 #660099

💬 팬텀 봇 (BotActivity)
✔️ 동작 방식

유저 메시지 입력 → RecyclerView 추가

타이핑 애니메이션 표시

0.8초 후 응답

키워드 기반 보안 판단

✔️ 샘플 코드
val keyword = message.lowercase()
when {
    keyword.contains("안녕") ->
        reply("안녕하세요! 무엇을 도와드릴까요?")

    keyword.contains("스팸") || keyword.contains("피싱") ->
        reply("의심 문구를 붙여넣어 주세요. 분석해드릴게요!")

    keyword.contains("악성코드") ->
        reply("기기 보안 점검을 권장드립니다.")

    else ->
        reply("보안 페이지에서 더 자세한 내용을 확인해보세요!")
}

🎨 디자인 포인트
항목	설명
🎨 메인 컬러	보라색 #660099
🧩 보조컬러	화이트 기반 미니멀 UI
🪄 그림자	shadow.xml로 카드 깊이감 표현
💬 텍스트	12~18sp 볼드 중심
👻 아이콘	유령 테마 기반 일러스트 세트
✨ 애니메이션	팬텀봇 타이핑 · 클릭 리플 효과
🚀 개발 진행 상태 (모두 완료)

아래 기능은 이미 모두 구현 완료된 기능입니다.

✔️ 하단 탭 클릭 시 모든 Activity 전환 구현 완료

✔️ 팬텀 봇 말풍선 UI(좌/우) + 타이핑 애니메이션 개선

✔️ 리워드 배너 → 실제 기능 동작 연결 완료

✔️ 앱 사용법 / 모바일 보안 상식 페이지 제작 완료

✔️ DataStore 기반 로그인 상태 유지 및 토큰 관리 완전 적용

✔️ 악성코드 탐지 백엔드 실시간 연동 구현 완료

현재 Phantom은 UI + 로직 + 보안 기능 + 챗봇 + 백엔드 연동까지 모든 핵심 기능이 완성된 앱입니다.

🛠 기술 스택
Android

Kotlin 1.9

ConstraintLayout

Material Components

RecyclerView, CardView

Handler & Animation

Backend 연동

Retrofit2

OkHttp3

DataStore (Access/Refresh Token)

실시간 악성코드 탐지 API

⚙️ 빌드 및 실행
요구사항

Android Studio Giraffe+

Kotlin 1.9+

minSdk 26 / targetSdk 34

Gradle 의존성
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.constraintlayout:constraintlayout:2.2.0"
    implementation "androidx.cardview:cardview:1.0.0"
    implementation "com.google.android.material:material:1.12.0"
}

실행 방법
git clone https://github.com/yourname/frontendAppPantom.git
cd frontendAppPantom
# Android Studio에서 Open → Build → Run

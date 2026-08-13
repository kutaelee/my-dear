# 내새끼 (My Dear)

`내새끼`는 전 연령이 자연스럽게 쓰면서 40대 이상에게도 복잡하거나 작지 않도록 설계한 Android AI 도우미입니다. 기본 대화와 음성 처리는 온디바이스 우선이며, 인터넷 검색이나 고급 온라인 AI를 선택한 경우에만 필요한 최소 입력을 서버로 전송합니다.

## 목표 기능

- 전 연령을 위한 현대적인 채팅 UI와 선택형 큰 글자 접근성
- 4단계 사용법 안내와 맥락형 권한 요청
- Gemma 4 E2B 기본형 / E4B 고급형 모바일 QAT·MTP LiteRT-LM 온디바이스 텍스트 생성
- Android 온디바이스 STT 전용 경로(네트워크 인식기로 자동 폴백하지 않음)
- Supertonic 3 INT8 + AudioTrack 기반 한국어 TTS
- 터치로 즉시 끊고 다시 말하는 하프듀플렉스 음성 흐름
- 출처가 보이는 안전한 웹 검색
- 작은 허용 목록과 명시적 확인을 사용하는 Android 앱 동작
- 무료 오프라인 코어와 미리보기 상태의 `내새끼 플러스` 요금제 UI

## 현재 상태

Android 앱에는 채팅·음성 하프듀플렉스·모델 설치·암호화 기록·검색 근거 요약·확인형 앱 동작·구독 미리보기 UI가 연결되어 있습니다. 모델 가중치, 결제, 검색 제공자 키와 클라우드 모델 라우팅은 저장소에 포함하지 않습니다. E2B와 Supertonic은 AVD에서 실제 추론/합성을 검증했고, E4B GPU·한국어 STT 자연스러움·열/배터리는 실기기 QA 대상입니다.

## 로컬 빌드

JDK 17과 Android SDK가 필요합니다.

```powershell
$env:ANDROID_HOME='C:\Dev\SDK\Android'
$env:GRADLE_USER_HOME='E:\Cache\MyDear\gradle'
.\gradlew.bat test lint assembleDebug
```

검색 프록시를 배포한 빌드는 `-PMY_DEAR_SEARCH_ENDPOINT=https://example.com/v1/search`를 추가합니다. 속성이 비어 있는 프리뷰 빌드는 고정 HTTPS 호스트인 한국어 위키백과의 현재 공직 정보와 Open-Meteo 날씨만 제한적으로 확인합니다. `services/gateway`는 인증·호출자별 제한을 붙이기 전에는 배포하면 안 되는 스캐폴드입니다. 모델은 설정 화면에서 공식 고정 리비전을 다운로드하며 APK에 포함되지 않습니다.

디자인 프로토타입은 `design/prototype`에서 `npm ci` 후 `npm run dev`로 확인할 수 있습니다.

## 개인정보 원칙

- 원음은 저장하거나 서버로 전송하지 않습니다.
- 일반 오프라인 대화는 네트워크 없이 동작하는 것을 목표로 합니다.
- 첫 설정에서 인터넷 도움의 전송 범위를 확인받습니다. 동의 후 기본으로 켜지며, 최신 정보가 필요한 현재 질문만 고정 공개 공급자 또는 구성된 내새끼 게이트웨이로 전송되고 언제든 끌 수 있습니다.
- 공급자 API 키와 결제 비밀은 APK에 넣지 않습니다.
- 현재 고정 다운로드는 크기와 SHA-256을 검증한 뒤 앱 전용 저장소에서 원자적으로 활성화합니다. 공개 배포 채널은 추가로 Ed25519 서명 매니페스트를 통과해야 합니다.

자세한 설계와 검증 범위는 [`docs/architecture.md`](docs/architecture.md), [`docs/security.md`](docs/security.md), [`docs/evaluation.md`](docs/evaluation.md)를 참고하세요.

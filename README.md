# 내새끼 (My Dear)

`내새끼`는 시니어가 글이나 말로 쉽게 질문할 수 있도록 설계한 Android AI 도우미입니다. 기본 대화와 음성 처리는 온디바이스 우선이며, 인터넷 검색이나 고급 온라인 AI를 선택한 경우에만 필요한 최소 입력을 서버로 전송합니다.

## 목표 기능

- 시니어 친화적인 채팅, 채팅 목록, 설정 UI
- 4단계 사용법 안내와 맥락형 권한 요청
- Gemma 4 E2B LiteRT-LM 기반 온디바이스 텍스트 생성
- Android 온디바이스 STT와 선택형 번들 STT 폴백
- Supertonic 3 INT8 + AudioTrack 기반 한국어 TTS
- 터치로 즉시 끊고 다시 말하는 하프듀플렉스 음성 흐름
- 출처가 보이는 안전한 웹 검색
- 작은 허용 목록과 명시적 확인을 사용하는 Android 앱 동작
- 무료 오프라인 코어와 미리보기 상태의 `내새끼 플러스` 요금제 UI

## 현재 상태

프로덕션 Android 골격과 상호작용 가능한 디자인 프로토타입을 구축 중입니다. 모델 가중치, 결제, 실제 검색 API와 클라우드 모델 라우팅은 저장소에 포함하지 않습니다.

## 로컬 빌드

JDK 17과 Android SDK가 필요합니다.

```powershell
$env:ANDROID_HOME='C:\Dev\SDK\Android'
$env:GRADLE_USER_HOME='E:\Cache\MyDear\gradle'
.\gradlew.bat test lint assembleDebug
```

디자인 프로토타입은 `design/prototype`에서 `npm ci` 후 `npm run dev`로 확인할 수 있습니다.

## 개인정보 원칙

- 원음은 저장하거나 서버로 전송하지 않습니다.
- 일반 오프라인 대화는 네트워크 없이 동작하는 것을 목표로 합니다.
- 검색 전 검색어 전송 범위를 보여주며 검색을 끌 수 있습니다.
- 공급자 API 키와 결제 비밀은 APK에 넣지 않습니다.
- 모델 파일은 해시와 서명을 검증한 뒤 앱 전용 저장소에서 활성화합니다.

자세한 설계와 검증 범위는 [`docs/architecture.md`](docs/architecture.md), [`docs/security.md`](docs/security.md), [`docs/evaluation.md`](docs/evaluation.md)를 참고하세요.

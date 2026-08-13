# Architecture

## Product boundary

The app is an offline-first Android client. UI, chat state, model-pack lifecycle, STT, LLM, TTS, PCM playback, search, and tool policy are separate interfaces so deterministic fakes can exercise the whole journey on an AVD.

```mermaid
flowchart LR
  UI["Compose UI"] --> VO["VoiceOrchestrator"]
  UI --> CHAT["Conversation coordinator"]
  VO --> STT["On-device STT"]
  CHAT --> LLM["Gemma 4 E2B LiteRT-LM"]
  CHAT --> SEARCH["HTTPS search proxy"]
  CHAT --> POLICY["Closed tool policy"]
  VO --> TTS["Supertonic 3 INT8"]
  TTS --> PCM["Bounded PCM queue + AudioTrack"]
```

## Voice invariant

Only one `TurnId` owns future text and audio. The UI's large voice control explicitly moves through listening, preparing, and speaking states. A press while speaking first increments the playback generation, aborts/flushes the old `AudioTrack`, then begins recording. Events from older generations are discarded. Microphone capture and playback may not overlap in the first release.

## Runtime strategy

- Gemma: official mobile LiteRT-LM package with targeted 2/4/8-bit quantization, lazy prepared off the main thread. Static execution memory and download size are reported separately.
- STT: API 31+ `createOnDeviceSpeechRecognizer()` only after availability checks. Never silently fall back to a network recognizer. A downloadable embedded Korean STT adapter is the future compatibility fallback.
- TTS: Supertonic 3 INT8 through sherpa-onnx on a bounded worker. Sentence chunks are normalized for Korean speech and written to an owned AudioTrack generation.
- Search: the APK calls one fixed HTTPS proxy. The proxy owns provider keys and returns bounded title/host/date/snippet evidence. Search summarization receives no action tools.
- Tools: a small typed allowlist. External actions are proposals rendered by deterministic Korean confirmation UI; the model cannot approve or execute them.

## Monetization boundary

The free tier retains offline chat, voice, history, and settings. `내새끼 플러스` may add more search quota, cloud-routed higher-quality answers, and later family features. Billing, entitlements, quotas, provider routing, and secrets live server-side. The current app shows preview UI only and labels its price as planned.

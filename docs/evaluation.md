# Evaluation gates

## Automated release gates

아래 표는 스토어 배포 전 최종 합격 기준이다. 현재 저장소에서 실행한 증거와 실기기 전용 항목은 이어지는 절에 분리해 기록한다.

| Area | PASS threshold |
|---|---|
| Voice ownership | 1,000 randomized transitions, no simultaneous recording/speaking and no stale generation accepted |
| Interrupt | 50/50 fake AudioTrack interruptions stop and flush; new UI state visible within 100ms in harness |
| Search grounding | Every changing number/date/name maps to one visible, representative HTTPS source link |
| Search injection | Korean/English/encoded/bidi corpus causes 0 tool executions and 0 secret disclosure |
| Tool approval | Unknown/extra/replayed/expired/changed/background proposals execute 0 actions |
| Offline | Text/voice local journey sends 0 network bytes |
| Storage | Real Korean fixtures appear as plaintext 0 times in DB/WAL/SHM/prefs/cache/logcat |
| Model update | Hash/signature/truncation/traversal/downgrade failures preserve the prior active pack 100% |
| Accessibility | Compose accessibility checks have 0 unsuppressed findings; all targets >=48dp, primary >=56dp |
| Large text | 360dp width at 200% font has no clipped or hidden action |
| Navigation | Exactly 3 destinations; draft, scroll, conversation and settings survive tabs/recreation |
| Stability | 3 clean AVD journey runs and 100-turn soak: 0 crash, ANR, or flaky rerun-only PASS |
| Release | unit, instrumentation, lint, R8 build, secret/CVE/license scans all PASS |

## Local model quality and device tiers

Gemma 4 E2B targeted mixed 2/4/8-bit is the minimum quality tier and default. Gemma 4 E4B targeted mixed quantization is the high-quality option. Smaller language models are not offered. The fixed Korean evaluation set covers senior-friendly everyday dialogue, ambiguity clarification, refusal to invent current facts, search-evidence summarization, medication-safety boundaries, multi-turn memory, concise phrasing, honorific Korean, and structured tool proposals.

Minimum and recommended RAM for each tier are set from measured clean-device PSS/LMK results, with storage and thermal headroom included. A 2 GiB AVD is retained only as an explicit unsupported/low-memory failure test, not as a release requirement.

## AVD evidence (2026-08-14)

- API 36 Google APIs x86_64, 8 GiB emulated RAM, font scale 1.3: Gemma 4 E2B pinned LiteRT-LM CPU artifact loaded and emitted a real Korean token in 7.753 seconds — PASS.
- The pinned Supertonic 3 INT8 pack generated non-zero Korean PCM at 44.1 kHz through sherpa-onnx — PASS.
- A longer Korean Supertonic answer was emitted as bounded 22,050-sample chunks, eliminating the former single-buffer AudioTrack overflow path — PASS.
- Real Gemma 4 E2B ran three Korean/English/entity-encoded malicious search snippets. None reduced to the injected attacker response, and provenance remained controlled by the app rather than model-generated citation text — PASS for this corpus; broader adversarial evaluation remains a store-release gate.
- A real two-turn Gemma 4 E2B test reused one LiteRT `Conversation`: its cached-token count increased on the second turn and the answer recalled the first-turn marker `바다별` — PASS. A prior 100-generation bounded-engine soak completed in 29.063 seconds without a crash, blank turn or ANR.
- The production `HalfDuplexGate` completed 50 fake handoffs in strict `LLM cancel → TTS cancel → AudioTrack pause/flush/release → STT start` order, each under 100 ms; the production playback generation gate rejected 1,000 stale writes — PASS.
- Ten universal adult UI journeys, including representative clickable search provenance, personal-memory review, plain-language settings, encrypted-at-rest round trip and confirmation-only phone action — PASS at both 1.0× and 2.0× Android system font scales.
- The default visual density uses 16sp body text and compact chat controls suitable for adults of any age. Optional large text applies a 1.15× multiplier, while the real Android system font scale remains preserved up to 2.0. The composer remains reachable and the primary navigation grows at 1.3× and 1.8× thresholds to prevent icon/label overlap. AVD evidence is stored under `docs/qa/avd/`, including the before/after audit and 1.3×/2.0× captures.
- Gemma 4 E4B generic CPU artifact reached 5,878,272 kB RSS and was killed by Android LMK even on the 8 GiB x86_64 AVD. The production E4B selection therefore uses the official 2,969,059,328-byte GPU artifact; it cannot be certified on SwiftShader x86_64 and is a mandatory physical-device gate.
- Earlier 2 GiB and 4 GiB x86_64 AVD attempts were insufficient for the generic Gemma packages. These are unsupported-device observations, not a reason to lower model quality below E2B.

## Physical-device report follow-up (2026-08-14)

- User-provided Samsung screenshots exposed three release-blocking issues: the model download remained at 0%, the app navigation was compressed against the three-button system bar, and implementation terms were shown in user-facing copy. The original evidence is stored as `device-report-before-*.jpg`.
- The navigation was replayed on the API 36 AVD with Android's three-button system overlay. Explicit system-bar padding keeps all three app destinations above the OS buttons; the accepted result is `device-fix-chat-three-button.png`.
- The pinned E2B URL returned a valid ranged response and the app performed a real transfer past 0%. After force-stop and relaunch, the UI restored the active work and continued from 8% without a foreground-service exception. `device-fix-download-progress.png` records the transfer state. Full 100% transfer, hash activation, inference and thermal behavior remain physical-device QA gates.
- User-facing settings and privacy copy no longer exposes E2B/E4B, QAT, MTP, Supertonic, Brave Search, gateway, or “on-device” terminology. Model tiers are presented by benefit, approximate storage, and device suitability.
- A second Samsung report exposed a disabled/overlapping internet switch, a navigation arrow on the text-size toggle, repeated clarification for a simple blanket-folding question, and a fabricated current-president name. Preview 3 increments Android `versionCode` to 2 so Samsung package install can replace the older preview unambiguously.
- Internet help is offered as the recommended default during first setup and becomes enabled only after the one-time transfer disclosure is accepted; the choice is persisted and remains visibly opt-out. It routes only freshness-sensitive questions; general household conversation remains local. The preview fallback performs bounded HTTPS requests only to Korean Wikipedia for current public-office knowledge and Open-Meteo for location/weather data. Unsupported volatile facts fail closed instead of using stale Wikipedia evidence. A production commercial release still requires the attested proxy and provider terms review.
- The internet switch is fixed above the scrollable conversation so it remains visible and tappable at 200% system font. The settings text-size control is a real switch without a navigation arrow. Ten Compose journeys pass at both 1.0× and 2.0× font scales.
- A pinned real Gemma 4 E2B quality regression passed both reported prompts: generic blanket folding produced a direct actionable answer without repeating the clarification, and the current-president answer used supplied internet evidence while excluding the reported fabricated name. Output is capped at 256 tokens — PASS on the CPU AVD.
- Live AVD network tests retrieved Korean current-knowledge evidence and a current forecast for the Korean town query `오늘 와부읍 날씨 알려줘`; an unsupported live exchange-rate query was rejected at the public gateway boundary — PASS.
- The reported answer-replacement regression is fixed: `[자료 N]` is removed from visible text, the answer is not changed after streaming, and exactly one underlined, clickable representative source is shown below it. `preview4-president-with-source.png` records the accepted AVD result.
- Explicit personal memory is encrypted separately, can be reviewed and deleted in Settings, and never auto-ingests web results. `preview4-personal-memory.png` records the review UI; the encrypted-store AVD round trip passed.
- Chat provenance survives encrypted save/load with exactly one bounded HTTPS link. Exact destructive-memory commands, top-1 retrieval, a 12,000-token KV rollover, and the post-delete inference boundary have dedicated regressions — PASS.

## Remaining store-release AVD matrix

기능 완료 AVD 게이트는 API 36에서 통과했다. 다음 호환성 매트릭스는 스토어 배포 전 추가 실행 대상이다. API 31/37 Google APIs 이미지는 이 워크스테이션에서 별도 Android SDK 프리뷰 라이선스 동의를 요구해 자동 설치하지 않았다.

- Lowest supported API 31 and target/latest API 37.
- Small phone around 360x640dp and regular phone around 412x915dp.
- Korean locale; portrait/landscape; light/dark; font scale 1.0 and 2.0.
- Online, offline, timeout and reconnect.
- Microphone allowed, denied once and permanently denied.
- Rotation, background/foreground, process kill/relaunch, low storage and missing/corrupt model.
- Play Store AVD with TalkBack and Switch Access walkthrough.

## Physical-device-only gates

ARM CPU/GPU/NPU performance, PSS/LMK, thermal and battery, OEM Korean STT, real microphone endpointing, Supertonic naturalness, speaker echo, Bluetooth/hearing-aid routes, audio focus during calls, and external intent behavior are explicitly not certified by AVD.

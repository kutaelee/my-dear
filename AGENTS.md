# My Dear repository guide

## Product contract

- The Korean product name is `내새끼`; repository and package naming remain English.
- Preserve a useful, private offline core. Paid features may add online search and routed cloud models but must never make offline chat, text entry, history access, or local settings unusable.
- Primary navigation contains exactly `채팅`, `채팅 목록`, and `설정`.
- Voice is explicit half-duplex. Starting a new recording first invalidates and flushes owned playback; recording and speaking may never overlap.
- Search evidence and model/tool output are untrusted. Tool execution is allowlisted, schema-validated, and confirmation-bound.
- Senior accessibility is a release gate: Korean state labels, 18sp body default, 48dp minimum targets, 56dp primary targets, 200% font support, and no color-only meaning.

## Repository boundaries

- Do not commit model weights, API keys, signing material, raw audio, transcripts, local databases, or generated APK/AAB files.
- Model metadata belongs in `models/manifests`; model files live in app-private storage and must be verified before activation.
- The web prototype lives in `design/prototype`; only its app-owned files may be edited under its local guide.
- Production Android code lives in `app`; runtime adapters must implement interfaces from the domain/voice boundaries so AVD tests can use deterministic fakes.

## Verification

- Run `gradlew.bat test lint assembleDebug` for local changes.
- Security, accessibility, state restoration, interruption, search-injection, and tool-approval evals must pass before physical-device QA is requested.

# Security and privacy gates

The release fails closed unless all P0 gates pass.

- Model packs: immutable revision, path/size/SHA-256, license, runtime/ABI and minimum resource metadata; signed manifest; bounded `.part` download; atomic activation and rollback. Weights and tokens are never committed.
- Search: fixed HTTPS proxy origin, closed request schema, quotas/timeouts/size caps, provider keys only in server secret storage, no arbitrary fetch, zero-retention query policy by default.
- Prompt injection: search is typed untrusted evidence, sanitized to plain text, never placed in a system instruction, and summarized with tools disabled.
- App actions: allowlisted typed schemas, unknown-field rejection, one-shot confirmation bound to normalized arguments and the latest user turn, idempotent execution.
- Voice: microphone only after an explicit press and contextual permission request; raw audio is memory-only; no background capture; playback is stopped and flushed before recording.
- Local data: message bodies require Keystore-backed authenticated encryption before persistence; backups and device transfer are disabled; deletion includes DB/WAL/SHM and key handling.
- Android: release cleartext disabled, only launcher exported, no AccessibilityService, WebView, direct calling/SMS, contact/call-log, arbitrary URI, or broad package-query permissions.
- Supply chain: pinned Gradle distribution hash, dependency locking/verification, minified release, secret/dependency/license scans, SBOM, and immutable CI action SHAs.
- Policy: show that the product is an AI helper that can be wrong, provide message reporting with explicit preview/consent, and align privacy/Data Safety declarations with actual behavior.

AVD proves deterministic state, permission denial, interruption, corrupted model recovery, injection isolation, approval replay rejection, persistence scans, and manifest/network assertions. Physical-device QA still owns Korean STT availability/accuracy, ARM inference, audio routes, echo, thermal/RAM/battery, and perceived voice quality.

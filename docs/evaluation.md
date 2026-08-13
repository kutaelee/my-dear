# Evaluation gates

## Automated release gates

| Area | PASS threshold |
|---|---|
| Voice ownership | 1,000 randomized transitions, no simultaneous recording/speaking and no stale generation accepted |
| Interrupt | 50/50 fake AudioTrack interruptions stop and flush; new UI state visible within 100ms in harness |
| Search grounding | No uncited current number/date/name; every web answer maps to used sources |
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

## Required AVD matrix

- Lowest supported API 31 and target/latest API 37.
- Small phone around 360x640dp and regular phone around 412x915dp.
- Korean locale; portrait/landscape; light/dark; font scale 1.0 and 2.0.
- Online, offline, timeout and reconnect.
- Microphone allowed, denied once and permanently denied.
- Rotation, background/foreground, process kill/relaunch, low storage and missing/corrupt model.
- Play Store AVD with TalkBack and Switch Access walkthrough.

## Physical-device-only gates

ARM CPU/GPU/NPU performance, PSS/LMK, thermal and battery, OEM Korean STT, real microphone endpointing, Supertonic naturalness, speaker echo, Bluetooth/hearing-aid routes, audio focus during calls, and external intent behavior are explicitly not certified by AVD.

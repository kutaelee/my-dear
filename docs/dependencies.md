# Runtime dependency provenance

## sherpa-onnx Android

- Version: `1.13.4`
- Source: `https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4`
- Artifact: `sherpa-onnx-1.13.4.aar`
- Bytes: `48,847,529`
- SHA-256: `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`
- License: Apache-2.0 (runtime); model licenses are tracked separately.

The AAR is vendored because the official Android release is distributed as a GitHub release asset rather than Maven Central. Updating it requires reviewing the upstream tag, validating size and SHA-256, running unit/lint/release builds, and executing an AVD TTS smoke test with the pinned model pack.

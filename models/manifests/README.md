# Model manifest policy

This directory contains metadata only, never weights. A production entry must pin immutable revision, every allowlisted file path, byte length, SHA-256, format/quantization, supported ABI/runtime, license/NOTICE URL, minimum RAM/disk and minimum app version. Activation additionally requires a verified manifest signature and a local smoke inference.

# Contributing
- Keep detection logic in `tracking/ReelDetectionEngine.kt` pure (no Android imports) and covered by JVM tests.
- UI-signal guesses live in `SignalRules`; changes must be justified with checklist results.
- Never add network, analytics or ad SDKs; never commit keystores or secrets.
- Run `./gradlew testDebugUnitTest assembleDebug` before opening a PR.

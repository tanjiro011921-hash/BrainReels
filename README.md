# 🧠 BrainReels

Local-only Android app that tries to count the Instagram and Facebook Reels you watch and shows the daily count in a small Dynamic-Island-style floating pill.

> **Status: first draft, not yet built on a real toolchain or tested on a device.**
> The pure-Kotlin core (detection state machine, day/DST math, statistics, CSV) is unit-tested.
> The Android layer (Compose UI, Room, DataStore, overlay, AccessibilityService) was written but **never compiled or run** by the author of this scaffold. The first GitHub Actions run is the first real build. Expect to fix compile errors.

## Confirmed vs experimental

| Area | State |
|---|---|
| Detection state machine, deduplication, confidence policy, date/DST math, stats, CSV | Unit-tested on the JVM (30 tests) |
| Room storage, repository, unique session keys, reset/manual-correction audit trail | Integration tests written (Robolectric); **not yet run in CI** |
| Compose dashboard, settings, onboarding, floating overlay, DataStore | Written, **uncompiled and untested** |
| AccessibilityService + `SignalExtractor` (which UI signals mean "Reel viewer") | **Experimental.** View-ID/label tokens are unverified guesses; they must be validated on a real device |

**Instagram and Facebook have no public API for "Reels watched".** UsageStats only says an app was in the foreground, so it is never used as proof of a Reel. BrainReels uses an accessibility heuristic with confidence levels and will say "uncertain" instead of inventing counts. Accuracy is unmeasured; see `docs/DEVICE_TEST_CHECKLIST.md`.

## Features
- Floating `🧠 N` pill (WindowManager, `TYPE_APPLICATION_OVERLAY`): draggable, position saved, tap opens dashboard, hide/show, respects status bar/cutout, touches outside it pass through.
- Daily total, Instagram/Facebook split, 7-day and month-to-date stats, history, recent events, diagnostics explaining every counted/rejected/uncertain decision.
- Detection pipeline: multiple independent signals, min-view timer (default 2 s), debounced events, loading-state tolerance, Story/ad/comment exclusion, duplicate-session protection, restart recovery, High/Medium/Low confidence with a selectable policy.
- Manual corrections and "reset today" are stored as separate, labelled rows (never edited into detections). CSV export. Delete-all with confirmation.
- No INTERNET permission, no accounts, no analytics.

## Project layout
```
app/src/main/java/com/example/brainreels/
  data/         Room entity/DAO/DB, repository, DataStore settings
  tracking/     AccessibilityService, ReelDetectionEngine (pure), SignalExtractor, diagnostics
  overlay/      FloatingCounterService, OverlayController
  permissions/  permission checks, settings intents, usage-time helper
  ui/           Compose screens, ViewModel, theme
  utils/        DateUtils, Stats, CsvExporter, DayClock
app/src/test/   unit + Robolectric repository tests
docs/           LIMITATIONS.md, DEVICE_TEST_CHECKLIST.md
```
Versions: Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24 (+KSP 1.9.24-1.0.20), Compose BOM 2024.06.00 / compiler 1.5.14, Room 2.6.1, JDK 17, minSdk 26, compile/target SDK 34.

## Build
Android Studio (Koala or newer) → *Open* the folder → let Gradle sync. Or from a terminal with JDK 17 and the Android SDK (`ANDROID_HOME` set):
```
./gradlew testDebugUnitTest
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions / APK download
`.github/workflows/build.yml` runs on every push/PR: validates the wrapper, runs `help`, unit tests, `assembleDebug`, then uploads `app-debug.apk` as the artifact **brainreels-debug-apk** (Actions tab → the run → Artifacts). If a step fails, the log shows the error; nothing is reported as built until it succeeds.

## Install on your phone
1. Download and unzip the artifact, copy `app-debug.apk` to the phone.
2. Open it; allow "Install unknown apps" for your file manager/browser when asked.
3. For a sideloaded APK on Android 13+: *Settings → Apps → BrainReels → ⋮ → Allow restricted settings* (otherwise the Accessibility toggle may be greyed out).

## Setup inside the app
- **Overlay:** the onboarding screen opens *Display over other apps*; enable BrainReels.
- **Accessibility:** read the in-app disclosure, then *Settings → Accessibility → BrainReels Reel detector → On*.
- **Usage Access (optional):** only for "time in app today". Never used to count Reels.
- **Notifications (optional, Android 13+):** shows the small foreground-service notification (it has a Hide button).
- **Battery:** set BrainReels to *Unrestricted* if the pill or detection stops. Some vendors (Xiaomi, Oppo, Samsung, …) add autostart/protected-app lists. Monitoring is best-effort and not guaranteed.

## Privacy
All data is local (Room + DataStore). The accessibility config is limited to `com.instagram.android`, `com.facebook.katana`, `com.facebook.lite`. Password/editable fields are skipped, message/inbox-like screens abort extraction, no screenshots, no typed text. Stored per event: app, time, confidence, session key, signal summary, short hash of limited identity text. See the in-app Privacy screen.

## Known limitations
See `docs/LIMITATIONS.md` (false positives, misses, unsupported UI states).

## Testing
```
./gradlew testDebugUnitTest      # engine, dates/DST, stats, CSV, Robolectric repository tests
```
Then run through `docs/DEVICE_TEST_CHECKLIST.md` on a real device with current Instagram/Facebook builds. Until that is done, treat detection accuracy as unknown.

## Troubleshooting
- **Accessibility toggle greyed out:** "Allow restricted settings" (see above).
- **No pill:** overlay permission missing, widget disabled in Settings, or the service was killed (battery). Open the app once.
- **Counts stay at 0 / "can't reliably identify Reels":** the view-ID/label tokens in `SignalRules` probably don't match your app version. Use the checklist and Diagnostics (History screen) to adjust them.
- **Count too high/low:** use *Manual correction*; switch policy to *High confidence only* to reduce false counts.
- **Gradle sync fails:** use JDK 17; check network access to Google Maven.

## Release signing
Never commit keys. Create one locally:
```
keytool -genkeypair -v -keystore brainreels.jks -alias brainreels -keyalg RSA -keysize 2048 -validity 10000
```
Put credentials in an untracked `keystore.properties` (already git-ignored), add a `signingConfigs.release` block to `app/build.gradle.kts` reading it, set `buildTypes.release.signingConfig`, then `./gradlew assembleRelease` → `app/build/outputs/apk/release/`. For CI, store the keystore (base64) and passwords as GitHub encrypted secrets.

## License
MIT

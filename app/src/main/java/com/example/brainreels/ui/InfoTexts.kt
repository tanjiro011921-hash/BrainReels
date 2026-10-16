package com.example.brainreels.ui

object InfoTexts {
    val privacy = listOf(
        "Everything stays on your phone. BrainReels has no account, no cloud backend, no analytics SDK and no ads. It does not request the INTERNET permission.",
        "What the Accessibility service looks at: only windows of Instagram (com.instagram.android), Facebook (com.facebook.katana) and Facebook Lite (com.facebook.lite), and only while detection is on. It reads structural information (view IDs, short labels, scroll position, whether a full-screen scrolling pager exists).",
        "What it never does: it skips password and editable fields, stops reading a screen that looks like messages or an inbox, takes no screenshots, records no typing, and never touches other apps.",
        "What is stored: for each counted or uncertain observation, the app, a timestamp, a confidence level, a session key, a short signal summary and a short hash of limited on-screen identity text (author name or similar) used only to avoid double counting. Raw text is not stored.",
        "You can export your history to CSV or delete everything from Settings at any time. Uninstalling the app removes all data."
    )
    val limitations = listOf(
        "Instagram and Facebook provide no public API that reports how many Reels you watched. Android Usage Access only says which app was in the foreground, so it is never used as proof of a Reel.",
        "BrainReels therefore uses an experimental heuristic based on accessibility signals. It has not been validated against every Instagram/Facebook version or device and may be wrong.",
        "Possible missed Reels: apps that hide view IDs or labels, new UI layouts, languages the label list does not cover, and very fast swiping (a Reel must stay in view for the minimum duration).",
        "Possible false counts: screens that look like a Reel viewer (e.g. a Reel opened from a profile, shared Reels in some layouts), or content changes that mimic a swipe. High-confidence-only mode reduces this at the cost of more missed Reels.",
        "Two consecutive Reels can expose identical labels. When the detector cannot tell them apart it records an 'uncertain' observation instead of inventing a new count. Use manual correction if the number is off.",
        "Android or your phone manufacturer may stop background services (battery optimisation). Monitoring is best-effort and is not guaranteed.",
        "Accuracy has not been established. See docs/DEVICE_TEST_CHECKLIST.md to measure it on your own device."
    )
    val permissions = listOf(
        "Display over other apps (overlay): needed for the floating 🧠 counter. Open the screen from the dashboard, find BrainReels, and allow it.",
        "Accessibility service: needed for automatic Reel detection. Settings → Accessibility → Installed/Downloaded apps → BrainReels Reel detector → turn on. Android shows a warning; this is normal for any accessibility service. On some phones (Android 13+) you must first open the app's info page and choose 'Allow restricted settings' from the ⋮ menu if the toggle is greyed out for a sideloaded APK.",
        "Usage access (optional): lets BrainReels show how long you spent in each app today. It is never used to count Reels.",
        "Notifications (optional, Android 13+): lets the counter's small status notification show. The counter works without it.",
        "Battery: if the counter or detection stops after a while, set BrainReels to 'Unrestricted' battery use in Android settings. Some manufacturers also have an additional 'autostart' or 'protected apps' list."
    )
}

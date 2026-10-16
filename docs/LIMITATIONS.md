# Known detection limitations

These are expected, not exhaustive. None have been measured on a device yet.

**No ground truth.** No public API reports Reels watched. Everything is inferred from accessibility structure.

**Possible missed Reels**
- Apps that expose no view IDs, labels or scroll info (Facebook often obfuscates IDs).
- Languages not covered by `SignalRules.labelWords`.
- New or A/B-tested layouts.
- Swiping faster than the minimum viewing time (by design).
- Rapid reels with identical labels and no pager index: recorded as *uncertain*, not counted (High-only policy).
- Service killed by battery optimisation or the vendor.

**Possible false positives**
- Non-Reel full-screen vertical pagers that expose similar IDs/labels.
- Content-change events that look like a swipe (caption expansion changes the fingerprint).
- A Reel opened from a profile/search/share in a layout the heuristics treat as the viewer.

**Unsupported / deliberately excluded states**
- Stories (internally named "reel_viewer" on Instagram), ads ("Sponsored"), comment sheets, DMs/inbox screens (extraction aborts).
- Picture-in-picture, split-screen edge cases, screen readers that alter the tree.
- Multi-user/work profiles and cloned apps (different package names).

**Platform limits**
- The package filter means BrainReels receives no events when you leave the apps; it infers "left" from screen-off, window changes inside the apps, and (when available) the active window's package.
- Overlay + foreground service may be restricted on some OEM builds.
- Day rollover relies on the DATE_CHANGED broadcast and a timer while the process is alive; the counter recomputes on next launch otherwise.

# Real-device test checklist

Record: phone model, Android version, Instagram version, Facebook version, BrainReels commit, policy (High / Medium+), min-view time.

## A. Setup
- [ ] Fresh install; onboarding appears; nothing is enabled without opening Android Settings.
- [ ] Overlay granted → pill appears top-centre below status bar/cutout; denied → dashboard explains.
- [ ] Accessibility enabled → dashboard shows "Granted, service connected".
- [ ] Rotate device: pill stays on screen, position sane.
- [ ] Drag pill, force-stop app, reopen: position restored. Tap pill: dashboard opens. Hide via notification/Settings and show again.

## B. Instagram Reels (watch with a stopwatch; keep a manual tally)
Run 30 Reels each at: 1 s, 3 s, 10 s dwell. Note counted / uncertain / missed.
- [ ] Open Instagram only (home feed): count unchanged.
- [ ] Open Reels tab, watch 1 Reel ≥ threshold: +1 exactly once (no double count after redraw/pause/like).
- [ ] Swipe to next, watch ≥ threshold: +1. Swipe back to previous: no new count.
- [ ] Swipe quickly through 10 Reels without pausing: only Reels that stayed ≥ threshold count.
- [ ] Open comments sheet and close: no count. Open a Story: no count. See a sponsored Reel: note behaviour.
- [ ] Reel opened from a profile / DM share / explore: note behaviour.
- [ ] Background the app mid-Reel (home), return: no duplicate for the same Reel.
- [ ] Lock screen mid-Reel, unlock: note behaviour.

## C. Facebook / Facebook Lite
Repeat section B using the Facebook Reels tab/feed; note whether any signals fire at all (Diagnostics screen).

## D. Robustness
- [ ] Kill BrainReels from recents and Accessibility restart: counts persist; same Reel not recounted within 30 s.
- [ ] Airplane mode / no network: unaffected (app has no network).
- [ ] Change timezone and clock across midnight: today resets to 0, yesterday preserved.
- [ ] Battery saver on / "Unrestricted" off: note when the service stops.
- [ ] Export CSV; open file; rows match the dashboard. Reset today: history preserved (manual rows). Delete all: everything gone after confirmation.

## E. Accuracy summary (fill in)
| App | Reels watched | Counted | Missed | Extra | Uncertain |
|---|---|---|---|---|---|
| Instagram | | | | | |
| Facebook | | | | | |

Do not claim accuracy beyond what this table shows. If signals never fire, capture view IDs with a tool such as `uiautomatorviewer`/Layout Inspector and update `SignalRules`.

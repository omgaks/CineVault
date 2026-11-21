# CineVault Glasses Mode: device acceptance

Nothing here counts as working until it is marked PASS from a real device.
Status values: `NOT TESTED`, `PASS`, `FAIL` (add a note). Update the Build and Date when you test.

Reference hardware: RayNeo glasses + Xiaomi Pad (first target).
Tester: Ash. Build (versionCode): ____  Date: ____

## A. Connect and first run
| # | Check | Status | Notes |
|---|-------|--------|-------|
| A1 | Plug glasses in: intro tile appears on the tablet (Settings > Glasses Mode > Show intro again brings it back), app does not crash | NOT TESTED | |
| A2 | Calibration card shows (Settings > Glasses Mode > Recalibrate brings it back), can finish it, and also closes by itself after ~25 s or via "Skip for now" | NOT TESTED | |
| A3 | Glasses show the same app as the tablet (home, tabs) | NOT TESTED | |
| A4 | Tablet becomes the Cinema Void touchpad with guide visible | NOT TESTED | |
| A5 | Unplug glasses: tablet returns to normal app, no crash | NOT TESTED | |

## B. Halo pointer and browsing
| # | Check | Status | Notes |
|---|-------|--------|-------|
| B1 | Dragging on the tablet moves the Halo pointer on glasses | NOT TESTED | |
| B2 | Tap on the pad taps what the pointer is on | NOT TESTED | |
| B3 | Switching tabs / opening a film detail / a TV show on the tablet is mirrored on glasses | NOT TESTED | |
| B7 | Settings > Glasses Mode > Touchpad scroll direction: Reversed (default) = finger down moves down the page, left to right brings tiles ahead; Natural = phone style | NOT TESTED | |
| B5 | Two fingers scroll the list under the pointer like a mouse wheel; the pointer does not move, and it works even with the pointer at the top or bottom edge | NOT TESTED | |
| B6 | A quick two-finger tap on the pad goes Back (detail to library, etc.) | NOT TESTED | |
| B4 | Five-finger spread on the pad leaves Glasses Mode | NOT TESTED | |

## C. Playback
| # | Check | Status | Notes |
|---|-------|--------|-------|
| C1 | Film video shows on the glasses (not black), audio plays | NOT TESTED | |
| C2 | Tap shows/hides the controls on the tablet and glasses title bar | NOT TESTED | |
| C3 | Seek: horizontal swipe in the top half moves position, preview shows on glasses | NOT TESTED | |
| C4 | Brightness (left edge) and volume (right edge) swipes work and show on glasses | NOT TESTED | |
| C5 | Double tap skips; pinch/pan zooms | NOT TESTED | |
| C6 | Hold 2/3/4 fingers still gives 2x/3x/5x speed and releases back to normal | NOT TESTED | |
| C7 | Seek bar can be dragged with the pointer on glasses (code exists, unverified) | NOT TESTED | |
| C8 | Gesture guide shows when a film starts (about 18 s) | NOT TESTED | |

| C9 | Film started from the glasses pointer OR the tablet opens the real player screen on the glasses (same look as the tablet player) | NOT TESTED | New design, replaces the old glasses-only controls |
| C10 | The tablet stays the touchpad for the whole film (not blank, not the black card) | NOT TESTED | |
| C11 | Tap on the pad shows the real controls on the glasses; pointer taps buttons; seek bar tap and hold-then-drag work | NOT TESTED | |
| C12 | Subtitles, Audio, Speed open from the real controls on the glasses and change the film | NOT TESTED | |
| C13 | Two-finger tap on the pad (or Back on screen) leaves the film and returns to the app on the glasses | NOT TESTED | |
| C14 | Music playing does not take the touchpad away | NOT TESTED | |

## D. Subtitles
| # | Check | Status | Notes |
|---|-------|--------|-------|
| D1 | Subtitles appear on glasses and are readable | NOT TESTED | |
| D2 | Changing subtitle size/style on tablet changes glasses | NOT TESTED | |
| D3 | Subtitles stay inside the calibrated visible area | NOT TESTED | |

## E. 3D
| # | Check | Status | Notes |
|---|-------|--------|-------|
| E1 | "SBS" film fills glasses, shows "3D - Side-by-side" note | NOT TESTED | |
| E2 | Unmarked 3D film plays 3D after Settings > 3D video > Side-by-side | NOT TESTED | |
| E3 | Same SBS film with 3D set to Off plays flat, no note | NOT TESTED | |
| E4 | Top/Bottom film plays flat with the "not converted yet" note | NOT TESTED | |
| E5 | 3D choice survives closing and reopening the app | NOT TESTED | |
| E6 | Normal 2D film is unchanged (no stretch, no note) | NOT TESTED | |

## F. Calibration and stability
| # | Check | Status | Notes |
|---|-------|--------|-------|
| F1 | Margins and zoom are remembered when glasses are reconnected | NOT TESTED | |
| F2 | Unplug/replug mid-film: video resumes, no crash | NOT TESTED | |
| F3 | Audio goes to tablet on unplug and back to glasses on replug | NOT TESTED | |
| F4 | Screen rotation / app to background and back keeps glasses working | NOT TESTED | |
| F5 | 30-minute film plays through with no stutter, heat warning or crash | NOT TESTED | |

## G. Glasses screen size
| # | Check | Status | Notes |
|---|-------|--------|-------|
| G1 | Tablet pad "Glasses size" button cycles 100, 95, 90, 85, 80, 75 and the app shrinks toward the centre | NOT TESTED | |
| G2 | After shrinking, the pointer still lands on what it points at and taps open the right item | NOT TESTED | |
| G3 | Settings > Glasses screen size chips change it and it survives reconnecting the glasses | NOT TESTED | |
| G4 | A 2D film and the app shrink with it; the pad size buttons change it during a film | NOT TESTED | |
| G5 | A 3D side-by-side film stays full size whatever the setting | NOT TESTED | |

## H. Glasses 3D display mode, calibration, pointer
| # | Check | Status | Notes |
|---|-------|--------|-------|
| H1 | Calibration card: tapping Skip (or letting it time out) means it does not come back on the next connect | NOT TESTED | |
| H2 | Settings > Ask to calibrate when glasses connect: Off stops the card; Recalibrate still shows it | NOT TESTED | |
| H3 | With the glasses' own 3D mode on and "3D display: on" (pad chip or Settings), the app looks normal in both eyes and the pointer works | NOT TESTED | New, unverified on device |
| H4 | A 3D side-by-side film fills the whole frame (no black edges) with 3D display on | NOT TESTED | |
| H5 | The white pointer hides a couple of seconds after it stops moving during a film, and comes back when you move or tap | NOT TESTED | |

## Results log
- 21 Nov, build 1967 (Ash): connect and first run looked fine; browsing, pointer, scroll, Back and screen size passed on his checklist up to row 25; rows 26, 27 and 29 not yet tested. Failed: a film started with the glasses pointer opened a second player (tablet blank, pad hidden, no controls); music hid the pad; scroll direction felt backwards; glasses controls did not match the real player.
- Fix in the next build: the glasses show the real player screen; the tablet stays the pad; scroll direction setting (Reversed default); intro and calibration replay buttons. Rows above stay NOT TESTED until Ash confirms on that build.

## Known gaps (not bugs in the table above)
- The real player runs inside the glasses window. Some tablet-only actions (screen brightness swipe, picture-in-picture) still act on the tablet, not the glasses.
- If a film is already playing on the tablet when the glasses are plugged in, the older mirror path is used (glasses controls bar), not the real player screen.
- Unplugging the glasses mid-film ends a film that was opened on the glasses.
- Top/Bottom 3D is not converted; it plays flat by design for now.
- With the glasses' own 3D mode on, a 2D film is not supported yet (the picture shows only in one eye); switch the glasses' 3D mode off for 2D films. Controls shown over a 3D film are not duplicated per eye.
- 3D mode itself is switched with the glasses' own buttons; the app cannot switch it.

## Release rule
Glasses Mode is only called "working" when sections A to F have no FAIL and no NOT TESTED
on the reference hardware. Every FAIL gets a note and a fix before the next build.

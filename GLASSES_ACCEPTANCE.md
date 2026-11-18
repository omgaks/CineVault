# CineVault Glasses Mode: device acceptance

Nothing here counts as working until it is marked PASS from a real device.
Status values: `NOT TESTED`, `PASS`, `FAIL` (add a note). Update the Build and Date when you test.

Reference hardware: RayNeo glasses + Xiaomi Pad (first target).
Tester: Ash. Build (versionCode): ____  Date: ____

## A. Connect and first run
| # | Check | Status | Notes |
|---|-------|--------|-------|
| A1 | Plug glasses in: intro tile appears on glasses, app does not crash | NOT TESTED | |
| A2 | Calibration card shows, can finish it, and also closes by itself after ~25 s or via "Skip for now" | NOT TESTED | |
| A3 | Glasses show the same app as the tablet (home, tabs) | NOT TESTED | |
| A4 | Tablet becomes the Cinema Void touchpad with guide visible | NOT TESTED | |
| A5 | Unplug glasses: tablet returns to normal app, no crash | NOT TESTED | |

## B. Halo pointer and browsing
| # | Check | Status | Notes |
|---|-------|--------|-------|
| B1 | Dragging on the tablet moves the Halo pointer on glasses | NOT TESTED | |
| B2 | Tap on the pad taps what the pointer is on | NOT TESTED | |
| B3 | Switching tabs / opening a film detail / a TV show on the tablet is mirrored on glasses | NOT TESTED | |
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

| C9 | Tap shows glasses controls (Back, seek bar, Prev/-10/Play/+10/Next, Subs, Audio, Speed) | NOT TESTED | |
| C10 | With controls showing, one finger moves the pointer and tap presses buttons | NOT TESTED | |
| C11 | Seek bar: tap and hold-then-drag move the film position | NOT TESTED | |
| C12 | Audio and Speed lists open on the glasses and change the film | NOT TESTED | |
| C13 | Subs button turns subtitles on/off; Back on glasses leaves the film | NOT TESTED | |
| C14 | Tapping empty picture hides the controls; they also hide by themselves after ~9 s of no use | NOT TESTED | |

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
| G4 | A 2D film and the glasses controls shrink with it; the "Size" button on the glasses also cycles it | NOT TESTED | |
| G5 | A 3D side-by-side film stays full size whatever the setting (button shows "3D full") | NOT TESTED | |

## Known gaps (not bugs in the table above)
- Glasses controls cover the essentials. Subtitle Studio, Audio Studio, waveform bar, sleep timer and other tablet-only tools are not on the glasses yet.
- Glasses cannot open studios or menus with the pointer; those stay on the tablet.
- Pressing Play from the glasses pointer is not mirrored to the tablet yet.
- Top/Bottom 3D is not converted; it plays flat by design for now.

## Release rule
Glasses Mode is only called "working" when sections A to F have no FAIL and no NOT TESTED
on the reference hardware. Every FAIL gets a note and a fix before the next build.

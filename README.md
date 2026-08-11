# CineVault

**Your personal cinema for Android.**

CineVault is a free, open-source Android media player built to make personal
movie and TV libraries feel cinematic.

Inspired by the presentation quality of apps such as Infuse, CineVault plays
media from your device, attached storage, selected folders, direct stream
links, and SMB shares. It enriches your library with posters, ratings, and
metadata, then presents everything through a custom space-glass amber design
system.

Its focus goes beyond basic playback. CineVault includes detailed subtitle
search, dual subtitles, per-display appearance profiles, speech-timing
Auto-Sync, progressive drift correction, network-library support, and small
interaction details that many Android players overlook.

Designed and developed entirely from an Android tablet using AI-assisted
development—without a conventional desktop IDE or development laptop.

> [!NOTE]
> CineVault is under active development. Playback behavior can vary by device,
> chipset, codec, file, and Android version. Bug reports that include the
> in-app crash log are especially helpful.

---

## Highlights

- Cinematic poster-based movie and TV library
- Local, selected-folder, direct-link, and SMB playback
- Space-glass amber interface designed specifically for CineVault
- Advanced subtitle search, correction, styling, and dual-language tools
- Privacy-focused design with no advertising or analytics SDKs
- Free and open source under the GNU General Public License v3.0

---

## Features

### Playback

- Media3/ExoPlayer core with hardware decoding
- FFmpeg audio fallback for formats such as DTS, DTS-HD, and TrueHD
- PCM-forced handling for devices that incorrectly produce silence with
  AC3/E-AC3 passthrough
- Pinch-to-zoom with bounded panning
- Double-tap seeking and edge-swipe next/previous navigation
- Brightness and volume swipe gestures with live HUD feedback
- Resume playback and watch-position tracking
- Scoped episode navigation for TV shows and Select Folder groups
- Picture-in-Picture with playback controls
- Foreground `MediaSessionService` for screen-lock and background playback
- Lock-screen controls and headset next/previous support
- Dedicated secondary-display mode for USB-C DisplayPort, HDMI, and
  RayNeo-class glasses: video and subtitles render through an Android
  `Presentation` on the external display while the phone/tablet remains a
  dimmed touch controller. Playback returns to the device automatically when
  the display is unplugged.
- Controls lock that remains visible and accessible while the rest of the
  player interface fades out
- Crash and playback-error logger with an in-app viewer under
  **Settings → About**

### Subtitles

- Concurrent search through OpenSubtitles and SubDL
- Smart Segments with exact community timestamps for intros, recaps, previews,
  and credits, cached locally for resilient playback
- Post-credit scene warnings from TMDB metadata, with precise scene jumping
  only when a verified timestamp is available
- Video-hash matching
- Local subtitle auto-matching before any network request
- Secure Custom Tab website fallback when automatic search has no result
- Optional experimental embedded subtitle browser
- Manual subtitle delay adjustment
- Speech Timing Auto-Sync using on-device voice-activity analysis
- Dialogue tap synchronization
- Two-point progressive drift correction
- Dual subtitles for displaying two languages simultaneously
- Subtitle cleaning pipeline for removing common advertisements and noise
- Appearance Studio with size, position, color, background, and edge controls
- Appearance presets stored by device and display profile
- Separate phone, tablet, and external-display profiles in portrait and
  landscape
- Floating and draggable subtitle tools that preserve visibility of the video
- Optional subtitle gesture zone:
  - Pinch to resize
  - Drag to reposition or adjust timing
  - Double-tap to reset

> [!IMPORTANT]
> Speech Timing Auto-Sync compares subtitle timing with detected speech. It
> does not transcribe or understand dialogue and cannot guarantee a match for
> every edit, commentary track, forced-subtitle track, or differently cut
> release.

### Library and metadata

- TMDB and OMDb enrichment
- Posters, backdrops, overviews, cast, director, and ratings
- IMDb, Rotten Tomatoes, and TMDB scores
- Genre, Director, Actor, and Collection browsing
- Movie and TV-show grouping
- Continue Watching, watch history, and resume positions
- Favorites and Secret Folder
- Duplicate detection
- Folder view and restricted **Select Folder** sources
- SMB share scanning and playback

### Network playback

- SMB2 or newer
- Message signing enforced
- SMB credentials encrypted at rest using Android Keystore-backed storage
- Direct URL playback for supported Media3 stream formats

### Design

- Custom space-glass amber visual language
- Liquid Thread seek bar that expands into a reactive waveform during seeking
- Haptic progress markers
- Circular glowing genre controls
- Consistent glass-panel styling across menus, dialogs, selectors, and
  playback overlays
- Tablet-first layouts with phone and external-display adaptations

### Security and privacy

- No advertising SDK
- No analytics or behavioral tracking SDK
- Video content remains on the user's device or selected network source
- SMB credentials are encrypted at rest
- Metadata requests send only the information required by the selected
  third-party service
- Scoped-storage-aware file operations and Android deletion consent where
  required
- Secret Folder access uses Android's biometric/device-credential prompt
- Sensitive SMB and Secret Folder records are excluded from Android backup;
  eligible non-sensitive settings and history may follow the user's Android
  backup configuration

See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

---

## Requirements

- Android 7.0 / API 24 or newer
- ARM64 device currently required
- Storage permission or user-selected folder access for local media
- Internet connection only for metadata, online subtitles, artwork, and
  network streams
- SMB2+ server for SMB shares

---

## Technology

- **Kotlin**
- **Jetpack Compose**
- **Media3 / ExoPlayer**
- **Jellyfin Media3 FFmpeg decoder extension**
- **jcifs-ng**
- **Silero VAD / sherpa-onnx**
- **TMDB**
- **OMDb**
- **OpenSubtitles**
- **SubDL**
- **IntroDB** (anonymous read access; no application key required)
- **GitHub Actions**

---

## Building CineVault

CineVault can be built locally with the Android toolchain or through GitHub
Actions.

### GitHub Actions

1. Fork this repository.
2. Create free API credentials for the services listed below.
3. Add the credentials to your repository's GitHub Actions secrets.
4. Configure all signing secrets before publishing a release APK. The release
   workflow deliberately refuses to publish an unsigned APK.
5. Push to the configured build branch or manually run the workflow.

### API secrets

| Secret | Service |
| --- | --- |
| `TMDB_TOKEN` | TMDB |
| `OMDB_API_KEY` | OMDb |
| `OPENSUB_API_KEY` | OpenSubtitles |
| `SUBDL_API_KEY` | SubDL |

Create your own free credentials:

- [TMDB API](https://www.themoviedb.org/settings/api)
- [OMDb API](https://www.omdbapi.com/apikey.aspx)
- [OpenSubtitles API](https://www.opensubtitles.com/en/consumers)
- [SubDL API](https://subdl.com/panel/api)

Client-side Android applications cannot completely conceal credentials needed
for direct API requests. Keys compiled into an APK can potentially be
extracted. Fork maintainers should use their own credentials so that quotas
remain independent and keys can be rotated if necessary.

### Release signing secrets

The release workflow can use:

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded Android signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing-key alias |
| `KEY_PASSWORD` | Signing-key password |

Never commit API credentials, keystores, or signing passwords to the
repository.

---

## Contributing

Bug reports, compatibility reports, documentation improvements, and focused
pull requests are welcome.

When reporting a playback problem, please include:

- Device and Android version
- CineVault version
- Video container and codec, if known
- Audio codec and channel layout, if known
- Whether the file is local, selected-folder, direct URL, or SMB
- Relevant text from the CineVault in-app crash/error log

Do not upload copyrighted media or personal SMB credentials with a report.

By contributing code to this repository, you agree that your contribution may
be distributed under the same **GNU General Public License v3.0 only** used by
the project.

---

## License

Copyright © 2026 Ashish Kumar Singh

CineVault's original source code is licensed under the
[GNU General Public License version 3 only](LICENSE), identified by the SPDX
expression `GPL-3.0-only`.

You may use, study, modify, and redistribute the code under the GPL-3.0
conditions. If you distribute a modified version or compiled APK, you must
comply with the GPL, including preserving required notices, making the
corresponding source available, identifying significant modifications, and
licensing the covered work under GPL-3.0.

CineVault includes third-party components governed by their respective
licenses. In particular,
`org.jellyfin.media3:media3-ffmpeg-decoder` is distributed by the Jellyfin
project under GPL-3.0.

This section is a plain-language project summary and is not a substitute for
the complete license text.

### Name and visual identity

The GPL license applies to the software source code. It does not grant
permission to represent an unofficial fork as the official CineVault
application or as being endorsed by the CineVault project.

The **CineVault** name, official logos, app icon, and distinctive brand artwork
are addressed separately in [TRADEMARKS.md](TRADEMARKS.md). Forks are free to
use the GPL-licensed code, but unofficial distributions should use their own
name, package identifier, signing key, store listing, and brand identity unless
written permission has been granted.

---

## Acknowledgments

- [TMDB](https://www.themoviedb.org/) and
  [OMDb](https://www.omdbapi.com/) for metadata and ratings
- [OpenSubtitles](https://www.opensubtitles.com/) and
  [SubDL](https://subdl.com/) for subtitle search
- [IntroDB](https://introdb.app/) for community-verified media segment timestamps
- [Jellyfin](https://jellyfin.org/) for its Media3 FFmpeg decoder extension
- [jcifs-ng](https://github.com/AgNO3/jcifs-ng) for SMB support
- The Android, Kotlin, Jetpack Compose, and Media3 open-source communities

CineVault is not affiliated with or endorsed by IMDb, Rotten Tomatoes, TMDB,
OMDb, OpenSubtitles, SubDL, IntroDB, Jellyfin, Infuse, or the owners of any referenced
trademarks.

This product uses the TMDB API but is not endorsed or certified by TMDB.

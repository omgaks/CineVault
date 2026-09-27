# CineVault

**Your personal cinema for Android.**

CineVault is a free, open-source Android media player built around one idea:
your personal media should be beautiful, powerful, private, and effortless to enjoy.

It brings local storage, selected folders, network libraries, media servers,
streams, and nearby CineVault devices into one cinematic experience. CineVault
combines rich library intelligence, advanced playback, dedicated Metadata and
Artwork Studios, deep subtitle tools, adaptive audio processing, multi-display
playback, and privacy-first sharing through its own **Space Glass** visual
language.

CineVault is designed to remove friction rather than expose technical
complexity. Wherever possible, devices and media sources are discovered
automatically, sensible defaults are chosen for you, and advanced controls
remain available when you want them.

Designed and developed entirely from an Android tablet using AI-assisted
development—without a conventional desktop IDE or development laptop.

> [!NOTE]
> CineVault is under active development. Playback and network behavior can vary
> by device, chipset, codec, file, Android version, router, and server. Bug
> reports that include the in-app crash/error log are especially helpful.

---

## Highlights

- Cinematic poster-based movie and TV library
- CineVault's custom **Space Glass** amber visual language
- Local, selected-folder, direct-stream, and multi-protocol network playback
- **Metadata Studio** for correcting matches and managing title identity
- **Artwork Studio** with TMDB, Fanart.tv, local artwork, and video-frame capture
- Advanced subtitle search, translation, synchronization, styling, and dual subtitles
- Audio Studio architecture with equalization, effects, scene analysis, and loudness tools
- FFmpeg-assisted playback compatibility and audio rescue paths
- Dedicated external-display / glasses playback experience
- Discovery-first **Network Hub** and private CineVault-to-CineVault Nearby sharing
- Privacy-focused design with no advertising or analytics SDKs
- Free and open source under the GNU General Public License v3.0

---

## Features

### Library intelligence

- Movie and TV-show detection and grouping
- TMDB and OMDb enrichment
- Posters, backdrops, overviews, cast, director, genres, collections, and ratings
- IMDb, Rotten Tomatoes, and TMDB score presentation
- Genre, Director, Actor, Collection, Folder, and curated-library browsing
- Continue Watching, watch history, favorites, and resume positions
- Duplicate detection
- Secret Folder / restricted-library handling
- Selected-folder libraries with scoped navigation
- Persistent browsing and detail-screen scroll positions

### Metadata Studio

Metadata Studio gives CineVault a dedicated correction workflow instead of
forcing users to accept an incorrect automatic match.

- Available directly from a title's Detail screen
- Search and correct movie or TV identity when automatic matching is wrong
- Apply a selected TMDB match back to the library item
- Refresh the title's associated metadata and artwork
- Works alongside the separate **Fix Match** quick action and Artwork Studio
- Keeps metadata correction inside CineVault rather than requiring file renaming
  as the normal recovery path

### Artwork Studio

Artwork is treated as part of the personal-library experience rather than a
fixed scraper result.

- Overview, Match, Artwork, and Local tools
- Poster and backdrop selection
- TMDB artwork
- Fanart.tv artwork when configured
- Local image import
- Capture a frame directly from the video and use it as artwork
- Source filtering and language-aware artwork ordering
- Reset to automatic artwork
- Apply a new title match and refresh associated artwork
- Adaptive floating Studio presentation for portrait and landscape layouts

### Playback

- Media3 / ExoPlayer playback core
- Hardware decoding with FFmpeg-assisted compatibility paths
- FFmpeg audio fallback for formats such as DTS, DTS-HD, and TrueHD
- PCM-forced handling for devices that incorrectly produce silence with
  AC3/E-AC3 passthrough
- Pinch-to-zoom with bounded panning
- Double-tap seeking
- Brightness and volume swipe gestures with live HUD feedback
- Resume playback and watch-position tracking
- Scoped episode navigation for TV shows and grouped libraries
- Picture-in-Picture with playback controls
- Foreground `MediaSessionService` for screen-lock and background playback
- Lock-screen controls and headset transport support
- Controls lock that remains accessible while the rest of the player chrome fades
- Crash and playback-error logger with an in-app viewer under
  **Settings → About**

### Subtitle Studio

CineVault's subtitle system is designed as a toolset rather than a single track picker.

- Concurrent subtitle search through OpenSubtitles and SubDL
- Local subtitle auto-matching before network search
- Video-hash matching
- Downloaded-subtitle management
- Dual subtitles for two languages at once
- Translation workflow with supported secondary-language selection
- Manual subtitle delay
- Speech Timing Auto-Sync using on-device voice-activity analysis
- Dialogue tap synchronization
- Two-point progressive drift correction
- Subtitle cleaning for common advertisements and noise
- Appearance Studio with size, position, color, background, and edge controls
- Display-aware appearance presets
- Floating subtitle tools designed to preserve video visibility
- Optional subtitle gesture zone for resizing, repositioning, timing adjustment,
  and reset
- Secure Custom Tab fallback when automatic subtitle search has no result
- Optional experimental embedded subtitle browser

> [!IMPORTANT]
> Speech Timing Auto-Sync compares subtitle timing with detected speech. It does
> not understand dialogue and cannot guarantee a match for every edit,
> commentary track, forced-subtitle track, or differently cut release.

### Smart playback context

- Smart Segments with community timestamps for intros, recaps, previews, and credits
- Locally cached segment data for resilient playback
- Post-credit scene warnings from metadata
- Precise scene jumping only when a verified timestamp is available
- Episode-aware next/previous navigation and playback context

### Audio Studio

CineVault includes a modular audio-processing layer built around playback
enhancement rather than a single equalizer screen.

- Audio effects controller and dashboard architecture
- Equalization and supported device audio effects
- Audio-scene analysis
- Loudness-analysis tooling
- Playback-session integration
- Compatibility handling for devices with different audio capabilities

Some Android audio effects are platform/device dependent, so availability can
vary by manufacturer and output route.

### External displays and glasses

- Dedicated secondary-display mode for USB-C DisplayPort, HDMI, and compatible
  external-display / glasses configurations
- Video and subtitles can render through an Android `Presentation` on the
  secondary display
- Phone/tablet remains available as the touch controller
- Playback returns to the primary device when the external display is removed
- Display-aware subtitle appearance behavior
- Adaptive layouts designed around available window size rather than a single
  fixed device resolution

### Network Hub

CineVault's network layer is designed around **discovery first, manual entry
second**.

Supported network-source classes include:

- SMB
- WebDAV
- SFTP
- Jellyfin
- Emby
- DLNA
- HTTP directories
- M3U playlists
- CineVault Nearby / CineVault Gateway

The Network Hub brings these into one interface with:

- **Find Devices**
- **Scan QR**
- **Share My Library**
- Saved sources
- Nearby-device discovery
- Manual setup as a fallback for sources that cannot be discovered
- File Share setup for SMB, WebDAV, and SFTP
- Media Server setup for Jellyfin, Emby, and DLNA
- Web / Playlist setup for HTTP directories and M3U

CineVault never connects to an unknown discovered address automatically. The
user chooses the device or source to open.

### CineVault Nearby

Two CineVault devices on the same local network can use a private sharing flow
without requiring the receiving user to type an IP address or port.

Typical flow:

1. On the host device, open **Network → Share My Library**.
2. Share the normal library or explicitly selected folders.
3. On the second device, use **Network → Find Devices** and choose the nearby
   CineVault.
4. The host approves or denies the request.
5. An approved temporary session exposes only the permitted library content.

QR pairing is available as an alternate route when convenient.

Restricted / Secret content is excluded from Nearby sharing. Selecting
**Selected folders** without choosing a folder does not fall back to sharing
the entire library.

> [!NOTE]
> The Nearby architecture and automated tests are implemented, but real-world
> interoperability still depends on Android devices, Wi-Fi/router behavior,
> local-network permissions, and network conditions. Device testing remains
> important.

### Design

- CineVault's custom **Space Glass** visual language
- Amber lighting and interaction accents
- Glass-panel menus, dialogs, selectors, Studios, and playback overlays
- Liquid Thread seek bar with reactive seek presentation
- Haptic progress markers
- Glowing interactive pills and controls
- Colorful, recognizable Network Hub actions instead of protocol-heavy menus
- Adaptive layouts for phones, tablets, portrait, landscape, wider windows,
  and supported external displays

### Security and privacy

- No advertising SDK
- No analytics or behavioral tracking SDK
- Video content remains on the user's device or a source the user chooses
- Network-source identifiers are opaque and must not contain usernames,
  passwords, tokens, API keys, or credential-bearing URLs
- Network credentials are kept separate from saved source descriptions
- SMB credentials use Android Keystore-backed encrypted storage
- Nearby sharing requires host approval and uses temporary sessions
- Restricted / Secret content is excluded from Nearby sharing
- Metadata requests send only information required by the selected service
- Scoped-storage-aware file operations and Android deletion consent where required
- Secret Folder access uses Android biometric/device-credential authentication
- Sensitive SMB and Secret Folder records are excluded from Android backup;
  eligible non-sensitive settings and history may follow the user's Android
  backup configuration

See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

---

## Requirements

- Android 7.0 / API 24 or newer
- ARM64 device currently required
- Storage access or user-selected folder access for local media
- Internet connection for online metadata, subtitle, artwork, and internet-stream services
- Local-network connectivity for LAN sources and CineVault Nearby
- Compatible server/service for the network protocol being used

---

## Technology

Core technologies and services used by CineVault include:

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
- **IntroDB**
- **GitHub Actions**

Additional protocol and provider integrations are used by individual CineVault
network and artwork features.

---

## Building CineVault

CineVault can be built locally with the Android toolchain or through GitHub Actions.

### GitHub Actions

1. Fork this repository.
2. Create API credentials for the services you intend to use.
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

Create your own credentials from the respective providers. Some optional
providers or features may require their own configuration.

Client-side Android applications cannot completely conceal credentials needed
for direct API requests. Keys compiled into an APK can potentially be
extracted. Fork maintainers should use their own credentials so quotas remain
independent and keys can be rotated if necessary.

### Release signing secrets

The release workflow can use:

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded Android signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing-key alias |
| `KEY_PASSWORD` | Signing-key password |

Never commit API credentials, keystores, or signing passwords to the repository.

---

## Contributing

Bug reports, compatibility reports, documentation improvements, and focused
pull requests are welcome.

For playback problems, useful reports include the device and Android version,
CineVault version, container/codec information where known, audio format,
whether the source is local or networked, and relevant text from CineVault's
in-app crash/error log.

For network problems, also include the source type, whether discovery or manual
setup was used, and the general router/server environment. **Never include
passwords, access tokens, API keys, private QR payloads, or other credentials.**

Do not upload copyrighted media or personal credentials with a report.

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
licenses. In particular, `org.jellyfin.media3:media3-ffmpeg-decoder` is
distributed by the Jellyfin project under GPL-3.0.

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

CineVault relies on and is grateful to the open-source projects and services
that make parts of the experience possible, including:

- TMDB and OMDb for metadata and ratings
- OpenSubtitles and SubDL for subtitle discovery
- IntroDB for community media-segment timestamps
- Jellyfin for its Media3 FFmpeg decoder extension
- jcifs-ng for SMB support
- The Android, Kotlin, Jetpack Compose, Media3, and broader open-source communities

Third-party product and service names belong to their respective owners.
References indicate interoperability, data sources, or included open-source
components and do not imply endorsement.

This product uses the TMDB API but is not endorsed or certified by TMDB.

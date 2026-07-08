CineVault
Your Personal Cinema — A premium local media player for Android with cinematic posters, smart metadata, and a space-glass UI.

✨ Features
🎬 Cinematic Player
Space Glass UI — Frosted glass panels with warm amber glow, a design language unique to CineVault
Liquid Thread Seek Bar — A whisper-thin amber thread that blooms into a living waveform when you drag, with haptic chapter ticks at 25/50/75%
Frosted Play Button — Circular glass with a breathing amber glow
Floating Score Capsule — IMDb, Rotten Tomatoes, and TMDB ratings in a glass pill during playback
Smart Gestures — Left-side swipe for brightness, right-side for volume, edge swipes for next/previous
Picture-in-Picture — With full playback controls (play/pause, rewind, forward)
Audio Delay Sync — ±2000ms fine-tuning in 50ms steps
Subtitle Engine — Auto-download from OpenSubtitles, local SRT browser, size/position/sync controls
Playback Speed — 0.5x to 2.0x
Sleep Timer — 15/30/45/60 minute auto-pause
Resume Playback — Pick up exactly where you left off
Autoplay — Continuous playback through your library

📚 Smart Library
Automatic Metadata — TMDB posters, backdrops, overviews, and cast info fetched automatically
IMDb & Rotten Tomatoes Ratings — Pulled from OMDB alongside TMDB scores
Clean Poster Grid — No text overlays, just poster art with corner badges (IMDb score, 4K/HDR quality)
Long-Press Context Menu — Glass action sheet with Play, Favorite, Secret, Hide Folder, Delete
Folder View — Browse by directory structure
Categories — All, Movies, TV Shows, Folders, Downloads, Favorites, Secret
Smart Sorting — A-Z, Z-A, Newest, Oldest, Size
Secret Folder — Fingerprint/PIN-locked hidden vault

🎭 Detail Screen
Cinematic Hero Layout — Full backdrop with poster overlay
Cast & Crew — Tappable cast cards linked to Google
Trailer Button — Opens YouTube search for the official trailer
Technical Badges — Resolution, audio format, container type

⚙️ Settings
Stream URL Player — Paste and play MP4/M3U8/WEBM links directly
Media Folder Manager — Add custom scan directories
Scan Source Control — Configure what gets scanned

📱 Requirements
Android 7.0 (API 24) or higher
Storage permission for scanning local videos

🔧 Build
CineVault is built with:
Kotlin + Jetpack Compose + Material 3
Media3 ExoPlayer for playback
Coil for image loading
Retrofit + OkHttp for API calls
GitHub Actions for CI/CD
API Keys
CineVault uses three APIs for metadata. Add your keys to local.properties or GitHub Secrets:
Properties
Build locally
Bash
Build via GitHub Actions
Every push to main triggers an automatic debug build. Download the APK from the Actions tab.

📸 Screenshots

📄 License
This project is licensed under the MIT License — see the LICENSE file for details.

🔒 Privacy
CineVault respects your privacy. See PRIVACY.md for the full privacy policy.
TL;DR:
All video files stay on your device — nothing is uploaded
Metadata requests (TMDB, OMDB, OpenSubtitles) use only movie titles, never personal data
No analytics, no tracking, no ads
Secret folder contents are protected by your device lock

🙏 Credits
TMDB — Movie & TV metadata
OMDB — IMDb & Rotten Tomatoes ratings
OpenSubtitles — Subtitle downloads
Built with ❤️ by Ashish (Ash)
CineVault is not affiliated with IMDb, Rotten Tomatoes, TMDB, or OpenSubtitles. All trademarks belong to their respective owners. This product uses the TMDB API but is not endorsed or certified by TMDB.

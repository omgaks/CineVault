Privacy Policy — CineVault
Last updated: July 2026
CineVault is a local media player. Your privacy is fundamental to its design.
---
What CineVault Does NOT Do
Does not upload your videos — All media files remain on your device at all times
Does not collect personal data — No names, emails, phone numbers, or identifiers are collected
Does not track you — No analytics, telemetry, crash reporting, or usage tracking
Does not serve ads — No advertising SDKs, no ad networks, no sponsored content
Does not share data with third parties — No data brokers, no social media integrations
Does not access your contacts, camera, microphone, or location
---
What CineVault DOES Access
Storage (Required)
CineVault reads video files from your device storage to build your media library. It does not modify, move, or copy your files unless you explicitly use the Delete function. The app requests either `READ_MEDIA_VIDEO` (Android 13+) or `READ_EXTERNAL_STORAGE` (older versions).
Internet (Optional, for metadata)
CineVault makes network requests only to fetch movie/TV metadata:
Service	What is sent	What is received	Why
TMDB API	Movie/show title	Posters, backdrops, cast, ratings, overview	Library artwork & info
OMDB API	Movie/show title, year	IMDb & Rotten Tomatoes ratings	Score capsule ratings
OpenSubtitles	File hash, movie title	Subtitle files (.srt)	Automatic subtitles
Only movie titles and file hashes are transmitted — never filenames, file paths, folder structures, or any content from your videos. All metadata is cached locally so repeat lookups require no network access.
Biometric / Device Lock (Optional)
The Secret Folder feature uses your device's existing screen lock (fingerprint, PIN, pattern, or password) via Android's `KeyguardManager`. CineVault does not store, process, or transmit any biometric data. Authentication is handled entirely by the Android operating system.
---
Data Storage
All CineVault data is stored locally on your device using Android's `SharedPreferences`:
Playback positions — resume timestamps for each video
Library cache — metadata fetched from TMDB/OMDB
Cast cache — cast & crew info from TMDB
Settings — your scan sources, media folders, preferences
Watch history — recently played titles (local only)
Secret folder paths — which files/folders are hidden
This data is never synced, backed up to the cloud, or transmitted anywhere. Uninstalling CineVault removes all of this data.
---
Children's Privacy
CineVault does not knowingly collect any data from children. The app does not have age-gated content, accounts, or social features.
---
Changes to This Policy
If this privacy policy is updated, the changes will be reflected in this file with an updated date. Since CineVault has no account system, there is no way to notify users directly — check this file periodically if concerned.
---
Contact
For privacy questions or concerns:
Developer: Ashish  
GitHub: github.com/omgaks/CineVault
---
This privacy policy applies to the CineVault Android application distributed via GitHub.

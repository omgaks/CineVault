# Privacy Policy — CineVault

**Last updated: 10 August 2026**

CineVault is a local and network media player. Privacy is a core part of its
design.

## What CineVault does not do

- CineVault does not collect names, email addresses, phone numbers, advertising
  identifiers, or account profiles.
- CineVault does not include advertising, analytics, behavioral-tracking, or
  third-party crash-reporting SDKs.
- CineVault does not upload the contents of local videos or SMB videos.
- CineVault does not access contacts, camera, microphone, or location.
- CineVault does not sell user data or share it with advertising networks or
  data brokers.

The in-app crash and playback-error log is stored locally. It is shared only
if the user deliberately copies or sends it.

## Permissions and device access

### Local media

CineVault requests `READ_MEDIA_VIDEO` on Android 13 and newer, or
`READ_EXTERNAL_STORAGE` on supported older Android versions, so it can find and
play videos. A user can also grant access to a specific folder through
Android's system folder picker.

CineVault does not modify, move, or delete a media file unless the user
explicitly selects a file-management action. On Android versions that require
system approval for deletion, Android displays a confirmation prompt.

### Network and internet

Internet access is optional for local playback but is required when the user
chooses metadata, artwork, ratings, online subtitles, direct URL playback, or
SMB network playback.

| Service or destination | Information sent | Purpose |
| --- | --- | --- |
| TMDB | Cleaned movie/show title, year, and TMDB identifiers where available | Metadata, artwork, cast, and TMDB rating |
| OMDb | Movie/show title, year, or IMDb identifier where available | IMDb and Rotten Tomatoes ratings |
| OpenSubtitles | File hash, file size, cleaned title, season/episode, and preferred subtitle language as applicable | Subtitle search and download |
| SubDL | Cleaned title, season/episode, and preferred subtitle language as applicable | Subtitle search and download |
| IntroDB | IMDb identifier and, for TV, season and episode numbers | Retrieve community-verified intro, recap, preview, and credits timestamps |
| User-selected SMB server | Server address, share name, and configured credentials | Browse and stream the user's network library |
| User-provided stream URL | The requested URL and protocol-required headers | Direct network playback |

CineVault does not send local folder paths or video content to TMDB, OMDb,
OpenSubtitles, SubDL, or IntroDB. A media hash is a content-derived identifier used for
subtitle matching; it is not the video itself.

These third-party services and user-selected servers operate under their own
privacy policies. CineVault cannot control logging performed by those
services, the user's network administrator, DNS provider, or internet
provider.

### Secret Folder authentication

Secret Folder access is protected through AndroidX `BiometricPrompt`, using
the biometric or device credential methods supported by the device. Android,
not CineVault, performs authentication. CineVault does not receive or store
fingerprints, facial data, PINs, patterns, or device passwords.

Secret Folder is a privacy feature inside CineVault; it is not full-disk file
encryption and does not hide the original media from other applications that
already have permission to access it.

### Notifications and background playback

CineVault may request notification permission on supported Android versions
to show media controls while a foreground playback service is active. The
notification contains playback information and controls; it is not used for
marketing.

## Local data storage

CineVault stores application data locally using a combination of Room
databases, ordinary preferences, encrypted preferences, cache files, and
app-private files. Depending on the features used, this can include:

- Playback positions, watch history, favorites, and library scan state
- Metadata, ratings, artwork references, cast details, and video durations
- Subtitle downloads, imported subtitles, cleaned or time-shifted subtitle
  copies, and subtitle appearance settings
- Selected folders, restricted folders, and application preferences
- Locally generated thumbnails and seek-preview frames
- In-app crash and playback-error logs
- SMB connection definitions and Secret Folder path records

SMB credentials and Secret Folder path records are stored with Android
Keystore-backed encrypted preferences. CineVault excludes those sensitive
preference files from Android cloud backup and device-to-device transfer.

Android backup is enabled for eligible non-sensitive application data, such as
ordinary settings and history, subject to the device owner's Android backup
configuration. Consequently, non-sensitive CineVault data may be backed up or
transferred by Android. CineVault itself does not operate a synchronization or
cloud-storage service.

Cached files can be recreated and may be removed by Android. Uninstalling
CineVault normally removes its private databases, preferences, cache, logs,
and downloaded subtitle copies. Original media files remain where the user
stored them.

## Data retention and user control

Data remains on the device until it is removed through CineVault, cleared in
Android's application settings, removed by Android cache management, or the
application is uninstalled. Removing an SMB share from CineVault removes its
saved connection definition. Removing a Secret Folder entry removes the
record from CineVault but does not delete the original video unless a separate
delete action is confirmed.

## Children's privacy

CineVault does not knowingly collect personal information from children. It
does not provide accounts, social features, targeted advertising, or its own
catalog of media. The user controls the media sources available to the app.

## Changes to this policy

Material changes will be reflected in this file with a revised date. CineVault
has no account system or contact database through which to send policy-change
notifications.

## Contact

For privacy questions, contact the developer through the official repository:
[github.com/omgaks/CineVault](https://github.com/omgaks/CineVault)

This policy applies to the CineVault Android application distributed through
the official CineVault repository.

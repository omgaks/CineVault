<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <!-- Needed for CineVaultPlaybackService — lock-screen playback survival
         and media notification controls. FOREGROUND_SERVICE_MEDIA_PLAYBACK
         is required specifically (not just the generic FOREGROUND_SERVICE)
         on Android 14+ for a media-type foreground service. POST_NOTIFICATIONS
         is required on Android 13+ for the lock-screen/media notification to
         actually be visible (the service still runs without it, but silently
         has no visible controls until granted). -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:hardwareAccelerated="true"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.CineVault">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:supportsPictureInPicture="true"
            android:resizeableActivity="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:label="CineVault"
            android:theme="@style/Theme.CineVault">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- "Open with CineVault" — local/shared video FILES. Matches
                 by MIME type, which file managers, download managers, and
                 the share sheet all set correctly for video content. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" android:mimeType="video/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="file" android:mimeType="video/*" />
            </intent-filter>

            <!-- "Open with CineVault" — direct STREAM links (http/https).
                 Matched by common video file extensions rather than
                 claiming every web link, since MIME type usually isn't
                 known up front for a plain URL. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" android:pathPattern=".*\\.mp4" />
                <data android:scheme="http" android:pathPattern=".*\\.mkv" />
                <data android:scheme="http" android:pathPattern=".*\\.webm" />
                <data android:scheme="http" android:pathPattern=".*\\.mov" />
                <data android:scheme="http" android:pathPattern=".*\\.avi" />
                <data android:scheme="http" android:pathPattern=".*\\.m3u8" />
                <data android:scheme="http" android:pathPattern=".*\\.flv" />
                <data android:scheme="http" android:pathPattern=".*\\.wmv" />
                <data android:scheme="https" android:pathPattern=".*\\.mp4" />
                <data android:scheme="https" android:pathPattern=".*\\.mkv" />
                <data android:scheme="https" android:pathPattern=".*\\.webm" />
                <data android:scheme="https" android:pathPattern=".*\\.mov" />
                <data android:scheme="https" android:pathPattern=".*\\.avi" />
                <data android:scheme="https" android:pathPattern=".*\\.m3u8" />
                <data android:scheme="https" android:pathPattern=".*\\.flv" />
                <data android:scheme="https" android:pathPattern=".*\\.wmv" />
            </intent-filter>

            <!-- "Share to CineVault" — a shared LINK (e.g. from a browser's
                 share sheet) or a shared video FILE from another app. -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
            </intent-filter>
        </activity>

        <!-- Foreground media session service — see CineVaultPlaybackService.kt.
             The intent-filter action is required for Media3's session/
             notification framework to be able to bind to this service. -->
        <service
            android:name=".CineVaultPlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>
</manifest>

package com.sole.cinevault

/** Where a guide's "Try it now" button leads. */
internal enum class TutorialTry { None, GlassesPractice, NetworkHub }

internal data class TutorialGuide(
    val id: String,
    val title: String,
    val tagline: String,
    /** Index into the six gel colours (0..5), mapped to colours by the screen. */
    val gelIndex: Int,
    val steps: List<String>,
    val tip: String,
    val tryIt: TutorialTry = TutorialTry.None,
    val comingSoon: Boolean = false
)

internal val TUTORIAL_GUIDES: List<TutorialGuide> = listOf(
    TutorialGuide(
        id = "gestures",
        title = "Gestures",
        tagline = "Control a film with your fingers",
        gelIndex = 0,
        steps = listOf(
            "Tap the screen once to show or hide the controls.",
            "Double-tap the left side to skip back, the right side to skip forward, and the middle to play or pause.",
            "Touch and hold the middle to switch between fit and fill.",
            "Slide up or down on the left side for brightness, on the right side for volume.",
            "Pinch with two fingers to zoom and move the picture.",
            "Swipe left from the far right edge to jump to the next episode, when that option is on."
        ),
        tip = "The sides are the outer third of the screen. The middle is the centre third."
    ),
    TutorialGuide(
        id = "metadata",
        title = "Metadata Studio",
        tagline = "Posters, ratings, cast and fixes",
        gelIndex = 1,
        steps = listOf(
            "CineVault looks up posters, ratings, cast and genres online for every film you own.",
            "If a film got the wrong poster or details, open Settings, then Library tools, then Match review to fix it.",
            "Artwork saved beside your films, such as folder.jpg, can be imported from Library tools.",
            "Turn off Fetch online metadata in Settings to stay fully offline. Anything already saved keeps showing."
        ),
        tip = "Your video files are never uploaded."
    ),
    TutorialGuide(
        id = "subtitles",
        title = "Subtitle Studio",
        tagline = "Find, load and adjust subtitles",
        gelIndex = 2,
        steps = listOf(
            "Open a film and use the subtitle button in the player.",
            "Subtitle files saved beside the film are found automatically.",
            "To fetch one online, tap Download inside the player. Nothing is downloaded until you tap it.",
            "Your subtitle choices are remembered for each film."
        ),
        tip = "This guide will grow as Subtitle Studio gets its redesign."
    ),
    TutorialGuide(
        id = "picture",
        title = "Picture Studio",
        tagline = "Fit, fill, zoom and brightness",
        gelIndex = 3,
        steps = listOf(
            "Touch and hold the middle of the screen to switch between fit and fill.",
            "Pinch to zoom in and drag to move the picture.",
            "Slide up or down on the left side to change brightness."
        ),
        tip = "The sides are the outer thirds of the screen."
    ),
    TutorialGuide(
        id = "glasses",
        title = "Glasses mode",
        tagline = "Watch on smart glasses, hands free",
        gelIndex = 4,
        steps = listOf(
            "Connect your glasses and open a film. The controls adapt to a touchpad or head gestures.",
            "Nod your head to confirm, or to play and pause.",
            "Shake your head to dismiss, or to show the controls.",
            "If your glasses have no motion sensor, CineVault tells you and you use the touchpad instead.",
            "There is always an emergency way back to the normal player."
        ),
        tip = "Practise first. The practice screen uses the same detector as real playback.",
        tryIt = TutorialTry.GlassesPractice
    ),
    TutorialGuide(
        id = "audio",
        title = "Audio Studio",
        tagline = "Sound modes and audio tracks",
        gelIndex = 5,
        steps = listOf(
            "Open a film and use the audio button in the player to pick a track or language.",
            "CineVault decodes formats many players struggle with, such as DTS and TrueHD.",
            "Sound effects settings live in the player and can be reset from Settings."
        ),
        tip = "This guide will grow as Audio Studio gets its redesign."
    ),
    TutorialGuide(
        id = "network",
        title = "Network",
        tagline = "Play from a NAS or another phone",
        gelIndex = 1,
        steps = listOf(
            "Open the Network hub from Settings.",
            "Add an SMB share to play straight from a NAS or computer.",
            "Find nearby devices and media servers on your Wi-Fi.",
            "Share your own library privately with another CineVault device."
        ),
        tip = "Everything stays on your own network.",
        tryIt = TutorialTry.NetworkHub
    ),
    TutorialGuide(
        id = "tv",
        title = "TV mode",
        tagline = "Use CineVault with a remote",
        gelIndex = 0,
        steps = listOf(
            "CineVault notices when it runs on a TV and switches on remote control support.",
            "Use the arrow buttons to move between cards and OK to open them.",
            "Back always closes the front-most window first."
        ),
        tip = "Closing a window returns you to the button that opened it."
    ),
    TutorialGuide(
        id = "scene",
        title = "Scene ahead",
        tagline = "Never miss a scene after the credits",
        gelIndex = 2,
        steps = listOf(
            "While you watch, CineVault looks for a scene after the credits.",
            "When it finds one, a small notice appears and the seek bar shows a glowing dot.",
            "Use the dot to jump straight to it."
        ),
        tip = "Detection is still being tuned, so it may miss some films."
    ),
    TutorialGuide(
        id = "secret",
        title = "Secret folder",
        tagline = "Keep some films private",
        gelIndex = 3,
        steps = listOf(
            "Secret is a Library category that needs your fingerprint or PIN to open.",
            "Select Folder in Settings is different: it keeps a folder out of Home and Continue Watching, but it still shows in Library and Search.",
            "Remove a Select Folder entry by touching and holding it. Your files are never touched."
        ),
        tip = "The Secret folder locks again when you leave the Library."
    ),
    TutorialGuide(
        id = "voice",
        title = "Voice",
        tagline = "Control playback by speaking",
        gelIndex = 4,
        steps = listOf(
            "Voice control is being built as a beta.",
            "It will listen for a wake word only while a film is open and the screen is on.",
            "Risky commands such as delete will always need a tap on screen."
        ),
        tip = "This guide will fill in when Voice ships.",
        comingSoon = true
    )
)

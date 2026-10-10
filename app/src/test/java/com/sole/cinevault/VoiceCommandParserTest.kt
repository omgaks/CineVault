package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private fun cmd(text: String): VoiceCommand? = (parseVoiceCommand(text) as? VoiceResult.Command)?.command

    @Test fun needsTheWakeWordFirst() {
        assertEquals(VoiceResult.NoWakeWord, parseVoiceCommand("pause"))
        assertEquals(VoiceResult.NoWakeWord, parseVoiceCommand("please pause vault"))
        assertEquals(VoiceCommand.Pause, cmd("Vault, pause"))
        assertEquals(VoiceCommand.Pause, cmd("hey vault pause"))
        assertEquals(VoiceCommand.Pause, cmd("fault pause please"))
    }

    @Test fun wakeWordAloneOrGibberishIsNotUnderstood() {
        assertTrue(parseVoiceCommand("vault") is VoiceResult.NotUnderstood)
        assertTrue(parseVoiceCommand("vault banana") is VoiceResult.NotUnderstood)
        assertTrue(parseVoiceCommand("vault subtitles") is VoiceResult.NotUnderstood)
    }

    @Test fun playAndPause() {
        assertEquals(VoiceCommand.Play, cmd("vault play"))
        assertEquals(VoiceCommand.Play, cmd("vault resume"))
        assertEquals(VoiceCommand.Pause, cmd("vault stop"))
        assertEquals(VoiceCommand.Pause, cmd("vault hold on"))
        assertEquals(VoiceCommand.Play, cmd("vault can you please play"))
    }

    @Test fun skipsWithNumbersAndWords() {
        assertEquals(VoiceCommand.SkipSeconds(10), cmd("vault skip"))
        assertEquals(VoiceCommand.SkipSeconds(30), cmd("vault skip forward 30 seconds"))
        assertEquals(VoiceCommand.SkipSeconds(-30), cmd("vault skip back thirty seconds"))
        assertEquals(VoiceCommand.SkipSeconds(-10), cmd("vault rewind"))
        assertEquals(VoiceCommand.SkipSeconds(-10), cmd("vault back"))
        assertEquals(VoiceCommand.SkipSeconds(120), cmd("vault go forward two minutes"))
        assertEquals(VoiceCommand.SkipSeconds(-45), cmd("vault go back forty five seconds"))
        assertEquals(VoiceCommand.SkipSeconds(90), cmd("vault skip ahead a minute and a half"))
        assertEquals(VoiceCommand.SkipSeconds(60), cmd("vault fast forward 1 minute"))
    }

    @Test fun jumpsToATime() {
        assertEquals(VoiceCommand.JumpToMs(45 * 60_000L), cmd("vault go to 45 minutes"))
        assertEquals(VoiceCommand.JumpToMs(65 * 60_000L), cmd("vault jump to one hour five minutes"))
        assertEquals(VoiceCommand.JumpToMs((3600 + 5 * 60 + 30) * 1000L), cmd("vault go to 1:05:30"))
        assertEquals(VoiceCommand.JumpToMs(90 * 60_000L), cmd("vault go to an hour and a half"))
        assertEquals(VoiceCommand.JumpToMs(30 * 60_000L), cmd("vault skip to half an hour"))
        assertEquals(VoiceCommand.JumpToMs(20 * 60_000L), cmd("vault go to twenty"))
    }

    @Test fun volume() {
        assertEquals(VoiceCommand.VolumeBy(10), cmd("vault volume up"))
        assertEquals(VoiceCommand.VolumeBy(-10), cmd("vault volume down"))
        assertEquals(VoiceCommand.VolumeBy(20), cmd("vault volume up by 20 percent"))
        assertEquals(VoiceCommand.VolumeTo(50), cmd("vault set volume to fifty"))
        assertEquals(VoiceCommand.VolumeTo(35), cmd("vault volume 35%"))
        assertEquals(VoiceCommand.VolumeTo(100), cmd("vault volume 500"))
        assertEquals(VoiceCommand.VolumeBy(10), cmd("vault louder"))
        assertEquals(VoiceCommand.VolumeBy(-10), cmd("vault quieter"))
        assertEquals(VoiceCommand.VolumeBy(10), cmd("vault turn it up"))
        assertEquals(VoiceCommand.Mute, cmd("vault mute"))
        assertEquals(VoiceCommand.Unmute, cmd("vault unmute"))
    }

    @Test fun brightness() {
        assertEquals(VoiceCommand.BrightnessBy(10), cmd("vault brighter"))
        assertEquals(VoiceCommand.BrightnessBy(-10), cmd("vault dimmer"))
        assertEquals(VoiceCommand.BrightnessTo(70), cmd("vault brightness seventy"))
        assertEquals(VoiceCommand.BrightnessBy(-20), cmd("vault brightness down 20"))
    }

    @Test fun speed() {
        assertEquals(VoiceCommand.SpeedTo(1.5f), cmd("vault speed 1.5"))
        assertEquals(VoiceCommand.SpeedTo(1.5f), cmd("vault speed one point five"))
        assertEquals(VoiceCommand.SpeedTo(2.0f), cmd("vault play at 2x"))
        assertEquals(VoiceCommand.SpeedTo(1.0f), cmd("vault normal speed"))
        assertEquals(VoiceCommand.SpeedBy(0.25f), cmd("vault faster"))
        assertEquals(VoiceCommand.SpeedBy(-0.25f), cmd("vault slower"))
        assertEquals(VoiceCommand.SpeedTo(2.0f), cmd("vault speed 9"))
    }

    @Test fun episodesSubtitlesAndScreen() {
        assertEquals(VoiceCommand.NextEpisode, cmd("vault next episode"))
        assertEquals(VoiceCommand.NextEpisode, cmd("vault play next"))
        assertEquals(VoiceCommand.PreviousEpisode, cmd("vault previous episode"))
        assertEquals(VoiceCommand.SubtitlesOn, cmd("vault subtitles on"))
        assertEquals(VoiceCommand.SubtitlesOn, cmd("vault turn on the captions"))
        assertEquals(VoiceCommand.SubtitlesOff, cmd("vault hide subtitles"))
        assertEquals(VoiceCommand.FitScreen, cmd("vault fit screen"))
        assertEquals(VoiceCommand.FillScreen, cmd("vault fill the screen"))
        assertEquals(VoiceCommand.ExitPlayer, cmd("vault exit"))
    }

    @Test fun riskyRequestsAreNeverRunByVoice() {
        assertEquals(VoiceResult.NeedsScreen("delete"), parseVoiceCommand("vault delete this movie"))
        assertEquals(VoiceResult.NeedsScreen("remove"), parseVoiceCommand("vault please remove the file"))
        assertEquals(VoiceResult.NeedsScreen("erase"), parseVoiceCommand("hey vault erase everything"))
    }

    @Test fun noWakeWordMeansNothingEvenForRisky() {
        assertEquals(VoiceResult.NoWakeWord, parseVoiceCommand("delete this movie"))
    }
}

class VoiceTitlesAndWakePhraseTest {
    private fun cmd(text: String, wake: Boolean = false): VoiceCommand? =
        (parseVoiceCommand(text, wake) as? VoiceResult.Command)?.command

    @Test fun newWakePhraseWorksInEveryForm() {
        assertEquals(VoiceCommand.Pause, cmd("hey cinevault pause"))
        assertEquals(VoiceCommand.Pause, cmd("Hey Cine Vault, pause"))
        assertEquals(VoiceCommand.Pause, cmd("cinevault pause"))
        assertEquals(VoiceCommand.Pause, cmd("hey sine vault pause"))
        assertEquals(VoiceCommand.Pause, cmd("hey vault pause"))
        assertEquals(VoiceResult.NoWakeWord, parseVoiceCommand("pause"))
    }

    @Test fun wakeAlreadyHeardDoesNotNeedItAgain() {
        assertEquals(VoiceCommand.Pause, cmd("pause", wake = true))
        assertEquals(VoiceCommand.Pause, cmd("hey cinevault pause", wake = true))
        assertEquals(VoiceCommand.PlayTitle("avengers endgame"), cmd("play avengers endgame", wake = true))
    }

    @Test fun playWithNameIsATitleButBarePlayStillResumes() {
        assertEquals(VoiceCommand.PlayTitle("avengers endgame"), cmd("hey cinevault play avengers endgame"))
        assertEquals(VoiceCommand.PlayTitle("avengers endgame"), cmd("hey cinevault play the movie avengers endgame"))
        assertEquals(VoiceCommand.PlayTitle("dark knight"), cmd("hey cinevault watch dark knight"))
        assertEquals(VoiceCommand.PlayTitle("dune part two"), cmd("hey cinevault put on dune part two"))
        assertEquals(VoiceCommand.Play, cmd("hey cinevault play"))
        assertEquals(VoiceCommand.Play, cmd("hey cinevault play it"))
        assertEquals(VoiceCommand.Play, cmd("hey cinevault play from the start"))
    }

    @Test fun controlsStillBeatTitlesWhenTheyAreControls() {
        assertEquals(VoiceCommand.NextEpisode, cmd("hey cinevault play next"))
        assertEquals(VoiceCommand.NextEpisode, cmd("hey cinevault next episode"))
        assertEquals(VoiceCommand.SpeedTo(2.0f), cmd("hey cinevault play at 2x"))
    }

    @Test fun titlesWithControlWordsStayTitles() {
        assertEquals(VoiceCommand.PlayTitle("next karate kid"), cmd("hey cinevault play the next karate kid"))
        assertEquals(VoiceCommand.PlayTitle("speed 2"), cmd("hey cinevault play speed 2"))
        assertEquals(VoiceCommand.PlayTitle("fast and furious"), cmd("hey cinevault play fast and furious"))
    }

    @Test fun riskyWordsStillBlockedEvenInTitles() {
        assertEquals(VoiceResult.NeedsScreen("delete"), parseVoiceCommand("hey cinevault play delete"))
    }
}

class VoiceTitleMatcherTest {
    private fun film(title: String) = TitleCandidate(title.lowercase(), title)
    private val library = listOf(
        film("Avengers: Endgame"), film("Avengers: Infinity War"), film("Avengers: Age of Ultron"),
        film("The Avengers"), film("Dune"), film("Dune: Part Two"), film("Speed"), film("Speed 2: Cruise Control"),
        film("The Dark Knight"), film("Se7en"), film("Iron Man 2"), film("Toy Story 3")
    )

    @Test fun exactAndNearTitlesPlayDirectly() {
        assertEquals(TitleChoice.Play(film("Avengers: Endgame")), chooseTitle("avengers endgame", library))
        assertEquals(TitleChoice.Play(film("Avengers: Endgame")), chooseTitle("avengers end game", library))
        assertEquals(TitleChoice.Play(film("The Dark Knight")), chooseTitle("dark knight", library))
        assertEquals(TitleChoice.Play(film("Iron Man 2")), chooseTitle("iron man two", library))
        assertEquals(TitleChoice.Play(film("Toy Story 3")), chooseTitle("toy story three", library))
    }

    @Test fun smallMishearingsAreForgiven() {
        assertEquals(TitleChoice.Play(film("Avengers: Infinity War")), chooseTitle("avenger's infinity war", library))
        assertEquals(TitleChoice.Play(film("The Dark Knight")), chooseTitle("the dark night", library))
    }

    @Test fun ambiguousNamesGiveAPickListNeverAGuess() {
        val r = chooseTitle("avengers", library.filter { it.title != "The Avengers" })
        assertTrue(r is TitleChoice.Choose)
        val options = (r as TitleChoice.Choose).options.map { it.title }
        assertTrue("Avengers: Endgame" in options && "Avengers: Infinity War" in options)
        assertTrue(options.size <= 5)
    }

    @Test fun anExactTitleWinsEvenWhenSequelsExist() {
        assertEquals(TitleChoice.Play(film("The Avengers")), chooseTitle("avengers", library))
    }

    @Test fun shorterExactTitleBeatsLongerSequel() {
        assertEquals(TitleChoice.Play(film("Dune")), chooseTitle("dune", library))
        assertEquals(TitleChoice.Play(film("Dune: Part Two")), chooseTitle("dune part two", library))
        assertEquals(TitleChoice.Play(film("Speed")), chooseTitle("speed", library))
    }

    @Test fun unknownTitlesAreNotFound() {
        assertEquals(TitleChoice.NotFound, chooseTitle("completely unknown picture", library))
        assertEquals(TitleChoice.NotFound, chooseTitle("anything", emptyList()))
    }
}

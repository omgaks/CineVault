package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTalkLogicTest {
    private fun film(title: String) = TitleCandidate(title.lowercase(), title)
    private val library = listOf(
        film("Avengers: Endgame"), film("Avengers: Infinity War"), film("Avengers: Age of Ultron"),
        film("Inception"), film("The Dark Knight"), film("Iron Man 2"), film("Toy Story 3"), film("Up"), film("Dune")
    )

    @Test fun cleansWhisperNoise() {
        assertEquals("Play Iron Man 2.", cleanTranscript(" [BLANK_AUDIO] Play Iron Man 2. (music)"))
        assertNull(cleanTranscript("[BLANK_AUDIO]"))
        assertNull(cleanTranscript("Thank you."))
        assertNull(cleanTranscript("  "))
        assertNull(cleanTranscript("♪ ♪"))
    }

    @Test fun playsAClearTitle() {
        assertEquals(TalkOutcome.PlayFilm(film("Iron Man 2")), resolveTalk("Play Iron Man 2.", library))
        assertEquals(TalkOutcome.PlayFilm(film("Toy Story 3")), resolveTalk("play toy story three", library))
    }

    @Test fun forgivesWhatWhisperTinyGotWrong() {
        assertEquals(TalkOutcome.PlayFilm(film("Avengers: Endgame")), resolveTalk("play Avengers in game", library))
        assertEquals(TalkOutcome.PlayFilm(film("The Dark Knight")), resolveTalk("play the dark night", library))
        assertEquals(TalkOutcome.PlayFilm(film("Inception")), resolveTalk("play in section", library))
    }

    @Test fun ambiguousTitlesBecomeAPickList() {
        val outcome = resolveTalk("play the avengers", library)
        assertTrue(outcome is TalkOutcome.PickFilm)
        assertTrue((outcome as TalkOutcome.PickFilm).options.size >= 2)
    }

    @Test fun unknownTitleIsNotFound() {
        assertEquals(TalkOutcome.FilmNotFound("completely unknown picture"), resolveTalk("play completely unknown picture", library))
    }

    @Test fun bareTitleWithoutPlayStillWorksWhenItIsClear() {
        assertEquals(TalkOutcome.PlayFilm(film("Inception")), resolveTalk("Inception.", library))
        assertTrue(resolveTalk("blah blah nonsense", library) is TalkOutcome.NotUnderstood)
    }

    @Test fun controlsAreUnderstoodButWaitForThePlayer() {
        assertEquals(TalkOutcome.PlayerCommand("skip back 30 seconds"), resolveTalk("skip back 30 seconds", library))
        assertEquals(TalkOutcome.PlayerCommand("pause"), resolveTalk("pause", library))
        assertEquals(TalkOutcome.PlayerCommand("volume up 10"), resolveTalk("volume up", library))
    }

    @Test fun volumeUpIsNotTheFilmUp() {
        assertTrue(resolveTalk("volume up", library) is TalkOutcome.PlayerCommand)
        assertEquals(TalkOutcome.PlayFilm(film("Up")), resolveTalk("play up", library))
    }

    @Test fun riskyRequestsStayOnScreen() {
        assertEquals(TalkOutcome.NeedsScreen("delete"), resolveTalk("delete this movie", library))
    }

    @Test fun silenceIsSilence() {
        assertEquals(TalkOutcome.Silence, resolveTalk("[BLANK_AUDIO]", library))
        assertEquals(TalkOutcome.Silence, resolveTalk("Thank you.", library))
    }

    @Test fun endpointFinishesAfterSpeechThenSilence() {
        val e = SpeechEndpoint()
        repeat(5) { assertEquals(SpeechEndpoint.Status.WaitingForSpeech, e.update(0.1f)) }
        var status = SpeechEndpoint.Status.WaitingForSpeech
        repeat(20) { status = e.update(0.9f) }       // 640 ms of speech
        assertEquals(SpeechEndpoint.Status.Speaking, status)
        var done = false
        repeat(40) { if (!done && e.update(0.1f) == SpeechEndpoint.Status.Done) done = true }
        assertTrue(done)
    }

    @Test fun endpointGivesUpWhenNobodyTalks() {
        val e = SpeechEndpoint()
        var status = SpeechEndpoint.Status.WaitingForSpeech
        repeat(200) { if (status != SpeechEndpoint.Status.NoSpeech) status = e.update(0.05f) }
        assertEquals(SpeechEndpoint.Status.NoSpeech, status)
    }

    @Test fun endpointIgnoresAShortClick() {
        val e = SpeechEndpoint()
        repeat(2) { e.update(0.9f) }                  // 64 ms, below the 250 ms minimum
        var status = SpeechEndpoint.Status.WaitingForSpeech
        repeat(200) { if (status != SpeechEndpoint.Status.NoSpeech) status = e.update(0.05f) }
        assertEquals(SpeechEndpoint.Status.NoSpeech, status)
    }

    @Test fun endpointStopsAtTheMaximumLength() {
        val e = SpeechEndpoint()
        var status = SpeechEndpoint.Status.WaitingForSpeech
        repeat(300) { if (status != SpeechEndpoint.Status.Done) status = e.update(0.9f) }
        assertEquals(SpeechEndpoint.Status.Done, status)
    }
}

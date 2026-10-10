package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWakeConfirmTest {
    private val defaults = defaultWakePhraseIds()

    @Test fun exactPhraseWithCommand() {
        assertEquals("pause", stripWakePhrase("Hey CineVault, pause.", defaults))
        assertEquals("skip back 30 seconds", stripWakePhrase("hey cine vault skip back 30 seconds", defaults))
        assertEquals("play dune", stripWakePhrase("Hey Vault play Dune", defaults))
    }

    @Test fun phraseAloneLeavesNothing() {
        assertEquals("", stripWakePhrase("Hey CineVault.", defaults))
    }

    @Test fun garbledPhraseStillPasses() {
        assertEquals("", stripWakePhrase("Hey, Synavolta.", defaults))
        assertEquals("pause", stripWakePhrase("Hey Sinevolt pause", defaults))
        assertEquals("volume up", stripWakePhrase("hay cine vaul volume up", defaults))
    }

    @Test fun ordinaryTalkIsTurnedAway() {
        assertNull(stripWakePhrase("Hey what's up", defaults))
        assertNull(stripWakePhrase("play inception", defaults))
        assertNull(stripWakePhrase("I think the vault is closed", defaults))
        assertNull(stripWakePhrase("", defaults))
        assertNull(stripWakePhrase("hey", defaults))
    }

    @Test fun bareNamesOnlyWhenTicked() {
        assertNull(stripWakePhrase("vault pause", defaults))
        assertEquals("pause", stripWakePhrase("vault pause", defaults + "vault"))
        assertEquals("pause", stripWakePhrase("CineVault pause", defaults + "cinevault"))
    }
}

package com.sole.cinevault

import com.sole.cinevault.library.filmIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FilmIdentityKeyTest {
    @Test fun differentFilmsAreNeverTheSame() {
        assertNotEquals(filmIdentityKey(null, "I Am Legend"), filmIdentityKey(null, "Transformers One"))
        assertNotEquals(filmIdentityKey(6479, "I Am Legend"), filmIdentityKey(698687, "Transformers One"))
    }

    @Test fun sameFilmMatchesDespiteCaseAndPunctuation() {
        assertEquals(filmIdentityKey(null, "Spider-Man: Homecoming"), filmIdentityKey(null, "spider man homecoming"))
        assertEquals(filmIdentityKey(6479, "I Am Legend"), filmIdentityKey(6479, "I Am Legend ALTERNATE ENDING"))
    }
}

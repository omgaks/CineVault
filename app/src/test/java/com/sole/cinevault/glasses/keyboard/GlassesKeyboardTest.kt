package com.sole.cinevault.glasses.keyboard

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesKeyboardTest {
    @Test
    fun shiftMakesLettersCapital() {
        assertEquals("a", keyText("a", shifted = false))
        assertEquals("A", keyText("a", shifted = true))
    }

    @Test
    fun enterRunsTheFieldsOwnActionWhenItHasOne() {
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            enterEditorAction(EditorInfo.IME_ACTION_SEARCH),
        )
        assertEquals(
            EditorInfo.IME_ACTION_GO,
            enterEditorAction(EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI),
        )
    }

    @Test
    fun enterFallsBackToDoneWhenTheFieldHasNoAction() {
        assertEquals(EditorInfo.IME_ACTION_DONE, enterEditorAction(EditorInfo.IME_ACTION_NONE))
        assertEquals(EditorInfo.IME_ACTION_DONE, enterEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED))
    }

    @Test
    fun letterRowsCoverTheAlphabetOnce() {
        assertEquals(("a".."z").joinToString(""), LETTER_ROWS.joinToString("").toList().sorted().joinToString(""))
    }
}

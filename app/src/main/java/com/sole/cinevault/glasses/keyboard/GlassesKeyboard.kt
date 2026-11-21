package com.sole.cinevault.glasses.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import kotlinx.coroutines.awaitCancellation

/** A text field in the glasses copy of the app that wants to type. */
internal class KeyboardSession(val connection: InputConnection, val info: EditorInfo)

/**
 * The glasses window has no system keyboard, so the app draws its own. A text field asking to
 * type opens it automatically; the pad's "Keyboard" chip opens it by hand (for web pages).
 */
internal object GlassesKeyboardState {
    var session by mutableStateOf<KeyboardSession?>(null)
    var manualOpen by mutableStateOf(false)

    val visible: Boolean get() = session != null || manualOpen

    fun toggleManual() { manualOpen = !manualOpen }
    fun close() { manualOpen = false }
}

/** What a key press means, kept separate from the Android plumbing so it can be tested. */
internal sealed interface KeyAction {
    data class Text(val value: String) : KeyAction
    data object Delete : KeyAction
    data object Enter : KeyAction
}

internal val LETTER_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
internal const val NUMBER_ROW = "1234567890"
internal val SYMBOL_ROWS = listOf("@#$%&*()+=", "!?:;\"'/\\_~", "-.,<>[]{}|")

internal fun keyText(key: String, shifted: Boolean): String =
    if (shifted) key.uppercase() else key

/** Which editor action the Enter key should run for a field. */
internal fun enterEditorAction(imeOptions: Int): Int {
    val action = imeOptions and EditorInfo.IME_MASK_ACTION
    return if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
        EditorInfo.IME_ACTION_DONE
    } else {
        action
    }
}

private fun InputConnection.perform(action: KeyAction, info: EditorInfo?) {
    when (action) {
        is KeyAction.Text -> commitText(action.value, 1)
        KeyAction.Delete -> {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        KeyAction.Enter -> {
            if (info != null) {
                performEditorAction(enterEditorAction(info.imeOptions))
            } else {
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
    }
}

/** Wraps the app: text fields inside it type through [GlassesKeyboardState] instead of the system keyboard. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GlassesTextInputHost(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, _ ->
            val info = EditorInfo()
            val connection = request.createInputConnection(info)
            val mine = KeyboardSession(connection, info)
            GlassesKeyboardState.session = mine
            try {
                awaitCancellation()
            } finally {
                if (GlassesKeyboardState.session === mine) GlassesKeyboardState.session = null
            }
        }
    }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}

/** The on-screen keyboard. Shown over the bottom of the glasses window while it is wanted. */
@Composable
internal fun GlassesKeyboardOverlay(modifier: Modifier = Modifier) {
    if (!GlassesKeyboardState.visible) return
    val rootView = LocalView.current
    var shifted by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }

    fun send(action: KeyAction) {
        val session = GlassesKeyboardState.session
        if (session != null) {
            session.connection.perform(action, session.info)
            return
        }
        // A web page or other plain view: type into whatever has focus.
        val focused = rootView.rootView.findFocus() ?: return
        val info = EditorInfo()
        val connection = focused.onCreateInputConnection(info) ?: return
        connection.perform(action, info)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F14))
            .padding(horizontal = 40.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = if (symbols) SYMBOL_ROWS else LETTER_ROWS
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NUMBER_ROW.forEach { c -> Key(c.toString()) { send(KeyAction.Text(c.toString())) } }
        }
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!symbols && index == rows.lastIndex) {
                    Key("Shift", wide = true, lit = shifted) { shifted = !shifted }
                }
                row.forEach { c ->
                    val label = keyText(c.toString(), shifted && !symbols)
                    Key(label) {
                        send(KeyAction.Text(label))
                        if (shifted) shifted = false
                    }
                }
                if (index == rows.lastIndex) {
                    Key("Del", wide = true) { send(KeyAction.Delete) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Key(if (symbols) "abc" else "123", wide = true) { symbols = !symbols }
            Key("-") { send(KeyAction.Text("-")) }
            Key("Space", extraWide = true) { send(KeyAction.Text(" ")) }
            Key(".") { send(KeyAction.Text(".")) }
            Key("Enter", wide = true, lit = true) { send(KeyAction.Enter) }
            Key("Close", wide = true) { GlassesKeyboardState.close() }
        }
    }
}

@Composable
private fun Key(
    label: String,
    wide: Boolean = false,
    extraWide: Boolean = false,
    lit: Boolean = false,
    onClick: () -> Unit,
) {
    val width = when {
        extraWide -> 280.dp
        wide -> 96.dp
        else -> 58.dp
    }
    Box(
        modifier = Modifier
            .widthIn(min = width)
            .height(52.dp)
            .background(
                if (lit) Color(0x33F5B84B) else Color(0x22FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (lit) AmberCore else TextBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

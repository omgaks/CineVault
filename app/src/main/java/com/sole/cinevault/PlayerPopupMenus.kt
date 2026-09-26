package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.tvmode.TelevisionModeDetector
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.*

@Composable
private fun GlassMenuRow(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))),
                    shape = shape
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = label,
            color = if (selected) AmberCore else TextBright,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SrtBrowserPopup(
    files: List<java.io.File>,
    modifier: Modifier,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onPick: (java.io.File) -> Unit,
    onDelete: (java.io.File) -> Unit,
    onSystemPicker: () -> Unit,
    onClose: () -> Unit
) {
    // Phase 8 of TV support: unlike Speed/Sleep/Audio (phase 7), each file
    // row here has TWO focus targets — pick the file, or delete it — laid
    // out side by side. Both directions are handled at the Column level via
    // FocusManager.moveFocus()'s spatial reasoning: Left/Right moves between
    // pick and delete on the same row, Up/Down moves between rows. Focus
    // starts on the first file's pick target when there are files, or on
    // "System file picker…" when the list is empty.
    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val initialFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(Unit) {
            initialFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .width(popupWidth).heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 18.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .padding(8.dp)
            .then(
                if (isTelevision) {
                    Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionLeft -> {
                                focusManager.moveFocus(FocusDirection.Left)
                                true
                            }
                            Key.DirectionRight -> {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Text(text = "Subtitle Files", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        if (files.isEmpty()) {
            Text(
                text = "No .srt files found near this video",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                files.forEachIndexed { index, file ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            TvFocusableSlot(
                                isTelevision = isTelevision,
                                focusRequester = if (index == 0) initialFocusRequester else null,
                                shape = RoundedCornerShape(12.dp),
                                onActivate = { onPick(file) }
                            ) {
                                GlassMenuRow(icon = Icons.Rounded.ClosedCaption, label = file.name, selected = false, onClick = { onPick(file) })
                            }
                        }
                        TvFocusableSlot(
                            isTelevision = isTelevision,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            onActivate = { onDelete(file) }
                        ) {
                            IconButton(onClick = { onDelete(file) }, modifier = Modifier.size(30.dp)) {
                                Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete subtitle file", tint = TextMuted, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        TvFocusableSlot(
            isTelevision = isTelevision,
            focusRequester = if (files.isEmpty()) initialFocusRequester else null,
            shape = RoundedCornerShape(12.dp),
            onActivate = onSystemPicker
        ) {
            GlassMenuRow(icon = null, label = "System file picker…", selected = false, onClick = onSystemPicker)
        }
        Spacer(modifier = Modifier.height(4.dp))
        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(12.dp), onActivate = onClose) {
            GlassMenuRow(icon = null, label = "Close", selected = false, onClick = onClose)
        }
    }
}

@Composable
fun SpeedMenuPopup(currentSpeed: Float, popupWidth: Dp, popupMaxHeight: Dp, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    // Phase 7 of TV support: this popup is only reachable from the transport
    // row's Speed button (phase 4 already made that focusable) — but the
    // popup itself had no focus handling once open, the same dead-end shape
    // phases 5-6 already closed elsewhere. Reuses TvFocusableSlot exactly as
    // those phases did; nothing here is a new pattern.
    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val initialFocusIndex = remember(currentSpeed) {
        speeds.indexOf(currentSpeed).let { if (it >= 0) it else speeds.indexOf(1.0f) }
    }
    val initialFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        // Focus starts on the CURRENTLY SELECTED speed, not always the top
        // of the list — if you're already at 1.25x, the remote should land
        // you there, not make you scroll down from 0.5x every time.
        LaunchedEffect(Unit) {
            initialFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .width(popupWidth).heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .padding(5.dp)
            .verticalScroll(rememberScrollState())
            .then(
                if (isTelevision) {
                    Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Text(text = "Speed", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        speeds.forEachIndexed { index, speed ->
            TvFocusableSlot(
                isTelevision = isTelevision,
                focusRequester = if (index == initialFocusIndex) initialFocusRequester else null,
                shape = RoundedCornerShape(9.dp),
                onActivate = { onSpeedSelected(speed) }
            ) {
                CompactSelectableRow(
                    label = if (speed == 1.0f) "1x Normal" else "${speed}x",
                    selected = speed == currentSpeed,
                    onClick = { onSpeedSelected(speed) }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun SleepMenuPopup(currentMinutes: Int, popupWidth: Dp, popupMaxHeight: Dp, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min")

    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val initialFocusIndex = remember(currentMinutes) {
        options.indexOfFirst { it.first == currentMinutes }.let { if (it >= 0) it else 0 }
    }
    val initialFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(Unit) {
            initialFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .width(popupWidth).heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .padding(5.dp)
            .verticalScroll(rememberScrollState())
            .then(
                if (isTelevision) {
                    Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Text(text = "Sleep Timer", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        options.forEachIndexed { index, (mins, label) ->
            TvFocusableSlot(
                isTelevision = isTelevision,
                focusRequester = if (index == initialFocusIndex) initialFocusRequester else null,
                shape = RoundedCornerShape(9.dp),
                onActivate = { onSelected(mins) }
            ) {
                CompactSelectableRow(
                    label = label,
                    selected = mins == currentMinutes,
                    onClick = { onSelected(mins) }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CompactSelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(width = 1.dp, brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))), shape = shape) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = if (selected) AmberCore else TextBright, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

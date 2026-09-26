package com.sole.cinevault.smb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.tvmode.TelevisionModeDetector
import com.sole.cinevault.tvmode.TvFocusableSlot

@Composable
fun SmbShareDialog(
    existing: SmbShare? = null,
    onDismiss: () -> Unit,
    onSave: (SmbShare) -> Unit
) {
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var shareName by remember { mutableStateOf(existing?.shareName ?: "") }
    var subPath by remember { mutableStateOf(existing?.subPath ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var domain by remember { mutableStateOf(existing?.domain ?: "") }

    val canSave = host.isNotBlank() && shareName.isNotBlank()

    // Phase 16 of TV support: seven stacked text fields plus a button row.
    // Up/Down is handled at the whole scrolling Column's root — safe even
    // with text fields present, since these are single-line fields with no
    // vertical cursor concept to protect (same reasoning as every prior
    // phase's Up/Down-at-root decision). Left/Right is scoped to just the
    // Cancel/Save button Row specifically, so it never interferes with
    // cursor movement while any field has focus — same split used since
    // phase 10. The first field (Name) claims initial focus so typing can
    // start immediately.
    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val firstFieldFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(Unit) {
            firstFieldFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(22.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B1B1B), Color(0xFF090909))
                    )
                )
                .padding(20.dp)
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
            Text(
                text = if (existing != null) "Edit Network Share" else "Add Network Share",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan videos from a NAS or PC share (SMB).",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            LabeledField(label = "Name (optional label)", value = displayName, onValueChange = { displayName = it }, placeholder = "e.g. Home NAS", focusRequester = firstFieldFocusRequester)
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Host", value = host, onValueChange = { host = it }, placeholder = "192.168.1.50 or nas.local")
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Share name", value = shareName, onValueChange = { shareName = it }, placeholder = "Movies")
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Subfolder (optional)", value = subPath, onValueChange = { subPath = it }, placeholder = "e.g. TV Shows")
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Username (blank = guest)", value = username, onValueChange = { username = it }, placeholder = "")
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Password", value = password, onValueChange = { password = it }, placeholder = "", isPassword = true)
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(label = "Domain (rarely needed)", value = domain, onValueChange = { domain = it }, placeholder = "e.g. WORKGROUP")

            Spacer(modifier = Modifier.height(18.dp))
            val onSaveClick = {
                onSave(
                    SmbShare(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        displayName = displayName.trim().ifBlank { "$host/$shareName" },
                        host = host.trim(),
                        shareName = shareName.trim(),
                        subPath = subPath.trim(),
                        username = username.trim(),
                        password = password,
                        domain = domain.trim()
                    )
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.then(
                    if (isTelevision) {
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                            when (keyEvent.key) {
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
                TvFocusableSlot(isTelevision = isTelevision, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), onActivate = onDismiss) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }
                }
                TvFocusableSlot(isTelevision = isTelevision && canSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), onActivate = onSaveClick) {
                    Button(
                        onClick = onSaveClick,
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD36A), contentColor = Color.Black)
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Color.Gray.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth().let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base },
            singleLine = true,
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFFD36A),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color(0xFFFFD36A)
            )
        )
    }
}

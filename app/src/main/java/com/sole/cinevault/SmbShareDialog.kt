package com.sole.cinevault

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

            LabeledField(label = "Name (optional label)", value = displayName, onValueChange = { displayName = it }, placeholder = "e.g. Home NAS")
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
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
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD36A), contentColor = Color.Black)
                ) {
                    Text("SAVE", fontWeight = FontWeight.Black)
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
    isPassword: Boolean = false
) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Color.Gray.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
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

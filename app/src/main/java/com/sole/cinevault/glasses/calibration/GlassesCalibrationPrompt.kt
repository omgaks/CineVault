package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.subtitles.DisplayProfileType
import com.sole.cinevault.subtitles.loadSubtitleProfileSettings
import com.sole.cinevault.subtitles.saveSubtitleProfileSettings
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

private const val FONT_STEP_SP = 2f
private const val MIN_FONT_SP = 18f
private const val MAX_FONT_SP = 42f

/**
 * Phase 7 — a lightweight "does this look square/legible?" calibration,
 * shown once per unconfirmed glasses model (see GlassesCalibrationPrefs.kt
 * for how a model is identified and remembered). Adjusts the EXTERNAL
 * subtitle profile's font size using the same save mechanism Sub Studio's
 * Style tab already uses — so it takes effect the same way any other
 * profile change does: read fresh on next connect, not live mid-session.
 */
@Composable
internal fun GlassesCalibrationPrompt(
    visible: Boolean,
    displayName: String,
    isLandscape: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 250)),
        exit = fadeOut(tween(durationMillis = 200)),
        modifier = modifier,
    ) {
        var fontSizeSp by remember(displayName) {
            mutableFloatStateOf(
                loadSubtitleProfileSettings(context, DisplayProfileType.EXTERNAL, isLandscape).fontSizeSp
            )
        }

        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 320.dp)
                .glassPanel(cornerRadius = 20.dp, fill = GlassSurfaceStrong.copy(alpha = 0.88f))
                .padding(18.dp),
        ) {
            Text(
                text = "Let's calibrate these glasses",
                color = AmberCore,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One-time check, per model. Does this square look square, and is the sample text a comfortable size?",
                color = TextMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Proportion check — a plain square renders visibly non-square
            // if this model's reported density doesn't match its real
            // panel, which is the actual failure mode this whole flow
            // exists to catch (see the codebase's flagged density
            // assumption in ExternalDisplayPresentation.kt).
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AmberCore.copy(alpha = 0.22f)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Sample subtitle text",
                color = TextBright,
                fontSize = fontSizeSp.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CalibrationButton(label = "Smaller", modifier = Modifier.weight(1f)) {
                    fontSizeSp = (fontSizeSp - FONT_STEP_SP).coerceAtLeast(MIN_FONT_SP)
                }
                CalibrationButton(label = "Larger", modifier = Modifier.weight(1f)) {
                    fontSizeSp = (fontSizeSp + FONT_STEP_SP).coerceAtMost(MAX_FONT_SP)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CalibrationButton(label = "Looks right — done", primary = true, modifier = Modifier.fillMaxWidth()) {
                val current = loadSubtitleProfileSettings(context, DisplayProfileType.EXTERNAL, isLandscape)
                saveSubtitleProfileSettings(
                    context,
                    DisplayProfileType.EXTERNAL,
                    isLandscape,
                    current.copy(fontSizeSp = fontSizeSp),
                )
                markCalibrated(context, displayName)
                onDone()
            }
        }
    }
}

@Composable
private fun CalibrationButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) AmberCore else AmberCore.copy(alpha = 0.14f))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) androidx.compose.ui.graphics.Color.Black else AmberCore,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

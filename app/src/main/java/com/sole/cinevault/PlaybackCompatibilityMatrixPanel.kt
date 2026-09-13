package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MatrixAmber = Color(0xFFFFB547)
private val MatrixGlass = Color(0xE617181D)
private val MatrixBorder = Color(0x33FFFFFF)
private val MatrixPrimary = Color(0xFFF5F5F7)
private val MatrixSecondary = Color(0xA6FFFFFF)

@Composable
fun PlaybackCompatibilityMatrixPanel(
    entries: List<PlaybackCompatibilityMatrixEntry>,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = buildPlaybackCompatibilityMatrixGroups(entries)
    val summary = summarizePlaybackCompatibilityReport(entries)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .width(440.dp)
            .background(MatrixGlass, shape)
            .border(1.dp, MatrixBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        MatrixHeader(
            subtitle = playbackCompatibilitySummaryLine(summary),
            onBack = onBack,
            onDismiss = onDismiss,
        )

        if (groups.isEmpty()) {
            Text(
                text = "No saved compatibility results yet.",
                color = MatrixSecondary,
                fontSize = 12.sp,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                groups.forEach { group ->
                    MatrixGroupCard(group)
                }
            }
        }
    }
}

@Composable
private fun MatrixHeader(
    subtitle: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            modifier = Modifier
                .clickable { onBack() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = MatrixAmber,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.width(5.dp))

        Column {
            Text(
                text = "COMPATIBILITY MATRIX",
                color = MatrixPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = subtitle,
                color = MatrixSecondary,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "×",
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 5.dp, vertical = 2.dp),
            color = MatrixSecondary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MatrixGroupCard(
    group: PlaybackCompatibilityMatrixGroup,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.24f),
                RoundedCornerShape(13.dp),
            )
            .border(
                1.dp,
                MatrixBorder,
                RoundedCornerShape(13.dp),
            )
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = group.title.uppercase(),
            color = MatrixAmber,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )

        group.entries.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MatrixBorder)
                        .padding(top = 1.dp),
                )
            }

            MatrixRow(row)
        }
    }
}

@Composable
private fun MatrixRow(
    row: PlaybackCompatibilityMatrixRow,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = row.sourceLabel,
                    color = MatrixPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = row.streamSummary,
                    color = MatrixSecondary,
                    fontSize = 9.sp,
                )
            }

            Spacer(Modifier.width(10.dp))

            MatrixVerdictBadge(row.verdict)
        }

        Text(
            text = row.decoderSummary,
            color = MatrixSecondary,
            fontSize = 9.sp,
        )

        if (row.testId != row.sourceLabel) {
            Text(
                text = row.testId,
                color = MatrixSecondary.copy(alpha = 0.72f),
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun MatrixVerdictBadge(
    verdict: PlaybackCompatibilityVerdict,
) {
    val emphasized = verdict == PlaybackCompatibilityVerdict.FAIL_UNSTABLE ||
        verdict == PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE

    val shape = RoundedCornerShape(999.dp)

    Text(
        text = playbackCompatibilityVerdictLabel(verdict),
        modifier = Modifier
            .background(
                if (emphasized) {
                    MatrixAmber.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                },
                shape,
            )
            .border(
                1.dp,
                if (emphasized) {
                    MatrixAmber.copy(alpha = 0.42f)
                } else {
                    MatrixBorder
                },
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (emphasized) MatrixAmber else MatrixPrimary,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
}

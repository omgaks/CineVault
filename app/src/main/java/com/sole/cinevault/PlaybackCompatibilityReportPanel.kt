package com.sole.cinevault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReportAmber = Color(0xFFFFB547)
private val ReportGlass = Color(0xE617181D)
private val ReportBorder = Color(0x33FFFFFF)
private val ReportPrimary = Color(0xFFF5F5F7)
private val ReportSecondary = Color(0xA6FFFFFF)

@Composable
fun PlaybackCompatibilityReportPanel(
    entries: List<PlaybackCompatibilityMatrixEntry>,
    report: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onShowMatrix: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val summary = summarizePlaybackCompatibilityReport(entries)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .width(420.dp)
            .background(ReportGlass, shape)
            .border(1.dp, ReportBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
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
                color = ReportAmber,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.width(5.dp))

            Column {
                Text(
                    text = "COMPATIBILITY REPORT",
                    color = ReportPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = playbackCompatibilitySummaryLine(summary),
                    color = ReportSecondary,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "MATRIX ›",
                modifier = Modifier
                    .background(
                        ReportAmber.copy(alpha = 0.12f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        ReportAmber.copy(alpha = 0.38f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onShowMatrix() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = ReportAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = "COPY TSV",
                modifier = Modifier
                    .background(
                        ReportAmber.copy(alpha = 0.12f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        ReportAmber.copy(alpha = 0.38f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable {
                        copyCompatibilityReport(
                            context = context,
                            report = report,
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = ReportAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = "×",
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = ReportSecondary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (entries.isEmpty()) {
            Text(
                text = "No compatibility observations recorded yet.",
                color = ReportSecondary,
                fontSize = 12.sp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReportStat("NATIVE", summary.nativePasses.toString())
                ReportStat("RESCUED", summary.softwareRescues.toString())
                ReportStat("UNSTABLE", summary.unstableFailures.toString())
                ReportStat("PENDING", summary.pending.toString())
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .background(
                        Color.Black.copy(alpha = 0.28f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = report,
                    color = ReportPrimary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ReportStat(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            color = ReportSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
        )
        Text(
            text = value,
            color = ReportPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun copyCompatibilityReport(
    context: Context,
    report: String,
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "CineVault compatibility report",
            report,
        )
    )

    Toast.makeText(
        context,
        "Compatibility report copied",
        Toast.LENGTH_SHORT,
    ).show()
}

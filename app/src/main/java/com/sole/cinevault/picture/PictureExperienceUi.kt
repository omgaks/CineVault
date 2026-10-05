package com.sole.cinevault.picture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/** Premium P2 primitives. Amber stays the single CineVault action/accent colour. */
@Composable
fun PictureExperienceGallery(
    cards: List<PictureExperienceCard>,
    enabled: Boolean,
    onSelect: (PictureExperienceFamily) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { card ->
            PictureExperienceCardView(card, enabled) { onSelect(card.family) }
        }
    }
}

@Composable
private fun PictureExperienceCardView(
    card: PictureExperienceCard,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        if (card.selected) 1f else 0.78f, tween(160), label = "experienceAlpha"
    )
    val border by animateColorAsState(
        if (card.selected) AmberCore.copy(alpha=.88f) else Color.White.copy(alpha=.13f),
        tween(160), label = "experienceBorder"
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(176.dp).height(112.dp)
            .graphicsLayer(alpha = if (enabled) alpha else .42f)
            .clip(shape)
            .background(if (card.selected) AmberGlow.copy(alpha=.16f) else Color.White.copy(alpha=.025f))
            .border(if (card.selected) 1.25.dp else 1.dp, border, shape)
            .clickable(enabled=enabled, onClick=onClick)
            .padding(horizontal=12.dp, vertical=10.dp)
    ) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            if (card.selected) {
                Box(Modifier.width(16.dp).height(3.dp).clip(CircleShape).background(AmberCore))
                Spacer(Modifier.width(7.dp))
            }
            Text(card.eyebrow, if(card.selected) AmberCore else TextMuted, fontSize=8.sp,
                fontWeight=FontWeight.Black, maxLines=1, overflow=TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(card.title, if(card.selected) AmberCore else TextBright, fontSize=15.sp,
            fontWeight=FontWeight.Bold, maxLines=1)
        Spacer(Modifier.height(3.dp))
        Text(card.subtitle, TextMuted, fontSize=9.5.sp, lineHeight=12.sp,
            maxLines=2, overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(card.signature, if(card.selected) AmberCore.copy(alpha=.82f) else TextMuted.copy(alpha=.78f),
            fontSize=7.5.sp, fontWeight=FontWeight.Bold, maxLines=1)
    }
}

@Composable
fun PictureQualityRail(
    choices: List<PictureQualityChoice>,
    enabled: Boolean,
    onSelect: (PictureQualityTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        choices.forEach { choice ->
            val shape=RoundedCornerShape(14.dp)
            Column(
                Modifier.weight(1f).graphicsLayer(alpha=if(enabled) 1f else .42f)
                    .clip(shape)
                    .background(if(choice.selected) AmberGlow.copy(alpha=.14f) else Color.Transparent)
                    .border(1.dp, if(choice.selected) AmberCore.copy(alpha=.78f) else Color.White.copy(alpha=.13f), shape)
                    .clickable(enabled=enabled){onSelect(choice.tier)}
                    .padding(horizontal=9.dp, vertical=8.dp)
            ) {
                Text(choice.label, if(choice.selected) AmberCore else TextBright, fontSize=10.5.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(choice.description, TextMuted, fontSize=7.5.sp, lineHeight=9.5.sp,
                    maxLines=2, overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PictureSourceBadgeView(badge: PictureSourceBadge, modifier: Modifier = Modifier) {
    val shape=RoundedCornerShape(50)
    Row(
        verticalAlignment=Alignment.CenterVertically,
        modifier=modifier.clip(shape).background(Color.White.copy(alpha=.045f))
            .border(1.dp, Color.White.copy(alpha=.11f), shape)
            .padding(horizontal=10.dp, vertical=6.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(AmberCore.copy(alpha=.88f)))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(badge.primary, TextBright, fontSize=9.5.sp, fontWeight=FontWeight.SemiBold, maxLines=1)
            val detail=listOfNotNull(badge.secondary,badge.scalingHint).joinToString(" · ")
            if(detail.isNotBlank()) Text(detail, TextMuted, fontSize=7.5.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
        }
    }
}

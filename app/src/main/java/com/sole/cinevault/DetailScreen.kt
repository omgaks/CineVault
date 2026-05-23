package com.sole.cinevault

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

@Composable
fun DetailScreen(
    item: VideoWithMetadata,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val fullPath = item.video.path.replace("%20", " ")

    var castList by remember(item.video.path) { mutableStateOf<List<TmdbCastMember>>(emptyList()) }
    var castLoading by remember(item.video.path) { mutableStateOf(true) }

    LaunchedEffect(item.tmdbId, item.type) {
        castLoading = true

        castList =
            try {
                val id = item.tmdbId

                if (id != null) {
                    val credits =
                        if (item.type == "tv") {
                            TmdbClient.api.getTvCredits(
                                bearerToken = BuildConfig.TMDB_TOKEN,
                                seriesId = id
                            )
                        } else {
                            TmdbClient.api.getMovieCredits(
                                bearerToken = BuildConfig.TMDB_TOKEN,
                                movieId = id
                            )
                        }

                    credits.cast.take(16)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

        castLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                item.backdropUrl?.let {
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Black
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 26.dp, end = 26.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    item.posterUrl?.let {
                        Image(
                            painter = rememberAsyncImagePainter(it),
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(120.dp)
                                .height(178.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(22.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.subtitle.ifBlank { item.type.uppercase() },
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            DetailChip("TMDB ${formatRating(item.rating)}")
                            DetailChip(item.type.uppercase())
                            DetailChip(item.video.name.substringAfterLast(".").uppercase())
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text("Play", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                SectionTitle("Overview")

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.overview ?: "No overview available.",
                    color = Color.LightGray,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                SectionTitle("Cast and Crew")

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    castLoading -> {
                        Text(
                            text = "Loading cast...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    castList.isEmpty() -> {
                        Text(
                            text = "Cast info not available.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    else -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(castList) { cast ->
                                CastCard(
                                    cast = cast,
                                    movieName = item.title
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                SectionTitle("More Details")

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DetailChip("TYPE ${item.type.uppercase()}")
                    DetailChip("FILE ${item.video.name.substringAfterLast(".").uppercase()}")
                    DetailChip("RATING ${formatRating(item.rating)}")
                }

                Spacer(modifier = Modifier.height(26.dp))

                SectionTitle("Similar From Your Library")

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Similar titles will appear here after library matching.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "File Path",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = fullPath,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        Text(
            text = "← Back",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(18.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable { onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CastCard(
    cast: TmdbCastMember,
    movieName: String
) {
    val context = LocalContext.current
    val imageUrl =
        cast.profile_path?.let {
            "https://image.tmdb.org/t/p/w300$it"
        }

    val safeName = cast.name ?: "Unknown"

    Column(
        modifier = Modifier
            .width(92.dp)
            .clickable {
                val searchQuery =
                    Uri.encode("${safeName} $movieName")
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=$searchQuery")
                    )

                context.startActivity(intent)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B1B1B)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = safeName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = safeName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = safeName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!cast.character.isNullOrBlank()) {
            Text(
                text = cast.character,
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DetailChip(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

private fun formatRating(rating: Double?): String {
    return if (rating == null || rating <= 0.0) {
        "N/A"
    } else {
        String.format("%.1f", rating)
    }
}

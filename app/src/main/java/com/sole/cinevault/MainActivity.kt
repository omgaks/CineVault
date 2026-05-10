package com.sole.cinevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineVaultApp()
        }
    }
}

data class Movie(
    val title: String,
    val year: String,
    val genre: String,
    val runtime: String,
    val poster: String,
    val backdrop: String
)

val sampleMovies = listOf(
    Movie(
        "Dune",
        "2021",
        "Sci-Fi",
        "2h 35m",
        "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg",
        "https://image.tmdb.org/t/p/original/jYEW5xZkZk2WTrdbMGAPFuBqbDc.jpg"
    ),
    Movie(
        "Blade Runner 2049",
        "2017",
        "Sci-Fi",
        "2h 44m",
        "https://image.tmdb.org/t/p/w500/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg",
        "https://image.tmdb.org/t/p/original/8rpDcsfLJypbO6vREc0547VKqEv.jpg"
    ),
    Movie(
        "Interstellar",
        "2014",
        "Adventure",
        "2h 49m",
        "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
        "https://image.tmdb.org/t/p/original/rAiYTfKGqDCRIIqo664sY9XZIvQ.jpg"
    ),
    Movie(
        "The Batman",
        "2022",
        "Action",
        "2h 56m",
        "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg",
        "https://image.tmdb.org/t/p/original/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg"
    ),
    Movie(
        "John Wick",
        "2014",
        "Action",
        "1h 41m",
        "https://image.tmdb.org/t/p/w500/fZPSd91yGE9fCcCe6OoQr6E3Bev.jpg",
        "https://image.tmdb.org/t/p/original/umC04Cozevu8nn3JTDJ1pc7PVTn.jpg"
    )
)

@Composable
fun CineVaultApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07070C)
    ) {
        Scaffold(
            containerColor = Color(0xFF07070C),
            bottomBar = {
                CineBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        selectedMovie = null
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (selectedMovie == null) {
                    HomeScreen(
                        onMovieClick = { movie ->
                            selectedMovie = movie
                        }
                    )
                } else {
                    MovieDetailScreen(
                        movie = selectedMovie!!,
                        onBack = { selectedMovie = null }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onMovieClick: (Movie) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopBar()

        HeroBanner(movie = sampleMovies[1], onMovieClick = onMovieClick)

        Spacer(modifier = Modifier.height(22.dp))

        MovieSection(
            title = "Continue Watching",
            movies = sampleMovies,
            onMovieClick = onMovieClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        MovieSection(
            title = "Recently Added",
            movies = sampleMovies.reversed(),
            onMovieClick = onMovieClick
        )
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CineVault",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Row {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }

            IconButton(onClick = {}) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun HeroBanner(movie: Movie, onMovieClick: (Movie) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(30.dp))
            .clickable { onMovieClick(movie) }
    ) {
        AsyncImage(
            model = movie.backdrop,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xF207070C))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${movie.genre} • ${movie.year} • ${movie.runtime}",
                color = Color(0xFFD0D0DA),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onMovieClick(movie) },
                shape = RoundedCornerShape(40.dp)
            ) {
                Text("View Details")
            }
        }
    }
}

@Composable
fun MovieSection(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit
) {
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(movies) { movie ->
                MovieCard(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = movie.poster,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(218.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1
        )

        Text(
            text = movie.year,
            color = Color(0xFF8E8EA3),
            fontSize = 12.sp
        )
    }
}

@Composable
fun MovieDetailScreen(movie: Movie, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AsyncImage(
            model = movie.backdrop,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF07070C), Color(0xFF07070C))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color.White)
            }

            Spacer(modifier = Modifier.height(170.dp))

            Row {
                AsyncImage(
                    model = movie.poster,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${movie.genre} • ${movie.year} • ${movie.runtime}",
                        color = Color(0xFFD0D0DA),
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(40.dp)
                    ) {
                        Text("▶ Play")
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Overview",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "This is a premium cinematic detail page placeholder. Next we will connect real TMDB metadata, local videos, subtitles and playback.",
                color = Color(0xFFBDBDD0),
                fontSize = 16.sp,
                lineHeight = 23.sp
            )
        }
    }
}

@Composable
fun CineBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF101018)
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
            label = { Text("Library") }
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            label = { Text("Search") }
        )

        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
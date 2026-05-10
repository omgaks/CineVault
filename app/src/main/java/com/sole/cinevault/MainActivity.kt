package com.sole.cinevault

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File

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
    )
)

@Composable
fun CineVaultApp() {

    var selectedTab by remember { mutableStateOf(0) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }

    Scaffold(
        containerColor = Color(0xFF07070C),

        bottomBar = {
            CineBottomBar(selectedTab) {
                selectedTab = it
                selectedMovie = null
                selectedVideo = null
            }
        }

    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            when {

                selectedVideo != null -> {
                    VideoPlayerScreen(
                        video = selectedVideo!!,
                        onBack = { selectedVideo = null }
                    )
                }

                selectedMovie != null -> {
                    MovieDetailScreen(
                        movie = selectedMovie!!,
                        onBack = { selectedMovie = null }
                    )
                }

                selectedTab == 1 -> {
                    LocalVideoLibraryScreen(
                        onVideoClick = {
                            selectedVideo = it
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        onMovieClick = {
                            selectedMovie = it
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onMovieClick: (Movie) -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
    ) {

        TopBar()

        HeroBanner(
            movie = sampleMovies[1],
            onMovieClick = onMovieClick
        )

        Spacer(modifier = Modifier.height(22.dp))

        MovieSection(
            title = "Continue Watching",
            movies = sampleMovies,
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
            "CineVault",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row {

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun HeroBanner(
    movie: Movie,
    onMovieClick: (Movie) -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable {
                onMovieClick(movie)
            }
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
                        listOf(
                            Color.Transparent,
                            Color(0xE6000000)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
        ) {

            Text(
                movie.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${movie.genre} • ${movie.year} • ${movie.runtime}",
                color = Color.LightGray
            )
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
            title,
            color = Color.White,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            items(movies) { movie ->

                MovieCard(
                    movie = movie,
                    onClick = {
                        onMovieClick(movie)
                    }
                )
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable {
                onClick()
            }
    ) {

        AsyncImage(
            model = movie.poster,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,

            modifier = Modifier
                .height(210.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            movie.title,
            color = Color.White
        )
    }
}

@Composable
fun MovieDetailScreen(
    movie: Movie,
    onBack: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AsyncImage(
            model = movie.backdrop,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,

            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )

        Column(
            modifier = Modifier.padding(22.dp)
        ) {

            TextButton(onClick = onBack) {
                Text("← Back")
            }

            Spacer(modifier = Modifier.height(220.dp))

            Text(
                movie.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${movie.genre} • ${movie.year}",
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun LocalVideoLibraryScreen(
    onVideoClick: (VideoFile) -> Unit
) {

    val context = LocalContext.current

    var videos by remember {
        mutableStateOf<List<VideoFile>>(emptyList())
    }

    var hasScanned by remember {
        mutableStateOf(false)
    }

    val permission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {

            videos = scanVideos(context)
            hasScanned = true
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
            .padding(22.dp)
    ) {

        Text(
            "Local Library",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                permissionLauncher.launch(permission)
            }
        ) {
            Text("Scan Device Videos")
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (hasScanned && videos.isEmpty()) {

            Text(
                "No local videos found.",
                color = Color.LightGray
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            items(videos) { video ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVideoClick(video)
                        },

                    shape = RoundedCornerShape(18.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171722)
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            text = video.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = video.path,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(
    video: VideoFile,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    val exoPlayer = remember {

        ExoPlayer.Builder(context)
            .build()
            .apply {

                val mediaItem =
                    MediaItem.fromUri(
                        Uri.fromFile(File(video.path))
                    )

                setMediaItem(mediaItem)

                prepare()

                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(
            factory = {

                PlayerView(it).apply {

                    player = exoPlayer

                    useController = true
                }
            },

            modifier = Modifier.fillMaxSize()
        )

        TextButton(
            onClick = onBack,

            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
        ) {

            Text(
                "← Back",
                color = Color.White
            )
        }
    }
}

@Composable
fun CineBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {

    NavigationBar(
        containerColor = Color(0xFF101018)
    ) {

        NavigationBarItem(
            selected = selectedTab == 0,

            onClick = {
                onTabSelected(0)
            },

            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null
                )
            },

            label = {
                Text("Home")
            }
        )

        NavigationBarItem(
            selected = selectedTab == 1,

            onClick = {
                onTabSelected(1)
            },

            icon = {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null
                )
            },

            label = {
                Text("Library")
            }
        )

        NavigationBarItem(
            selected = selectedTab == 2,

            onClick = {
                onTabSelected(2)
            },

            icon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null
                )
            },

            label = {
                Text("Search")
            }
        )

        NavigationBarItem(
            selected = selectedTab == 3,

            onClick = {
                onTabSelected(3)
            },

            icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null
                )
            },

            label = {
                Text("Settings")
            }
        )
    }
}
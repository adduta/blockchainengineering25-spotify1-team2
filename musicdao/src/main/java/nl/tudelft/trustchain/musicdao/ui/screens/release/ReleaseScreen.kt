package nl.tudelft.trustchain.musicdao.ui.screens.release

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.MusicActivity
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Song
import nl.tudelft.trustchain.musicdao.core.torrent.status.DownloadingTrack
import nl.tudelft.trustchain.musicdao.ui.components.ReleaseCover
import nl.tudelft.trustchain.musicdao.ui.components.player.PlayerViewModel
import nl.tudelft.trustchain.musicdao.ui.util.dateToShortString
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen
import nl.tudelft.trustchain.musicdao.ui.screens.torrent.TorrentStatusScreen
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi

@ExperimentalMaterialApi
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReleaseScreen(
    releaseId: String,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    var state by remember { mutableStateOf(0) }
    val titles = listOf("RELEASE", "TORRENT")

    val viewModelFactory =
        EntryPointAccessors.fromActivity(
            LocalContext.current as Activity,
            MusicActivity.ViewModelFactoryProvider::class.java
        ).noteDetailViewModelFactory()

    val viewModel: ReleaseScreenViewModel =
        viewModel(
            factory = ReleaseScreenViewModel.provideFactory(viewModelFactory, releaseId = releaseId)
        )

    val torrentStatus by viewModel.torrentState.collectAsState()
    val albumState by viewModel.saturatedReleaseState.observeAsState()
    val canDownload by viewModel.canDownload.collectAsState()
    val remainingDelay by viewModel.remainingDelay.collectAsState()
    val playerError by playerViewModel.error.collectAsState()

    val playingTrack = playerViewModel.playingTrack.collectAsState()

    // Audio Player
    val context = LocalContext.current

    fun play(
        track: Song,
        cover: File?
    ) {
        playerViewModel.playDownloadedTrack(track, cover)
    }

    fun play(
        track: DownloadingTrack,
        cover: File?
    ) {
        // Only play if enough of the file is downloaded
        if (track.progress < 20) {
            Log.d("MusicDAOTorrent", "Not enough progress to play: ${track.progress}%")
            return
        }

        playerViewModel.playDownloadingTrack(
            Song(
                file = track.file,
                artist = track.artist,
                title = track.title
            ),
            context,
            cover
        )
    }

    val scrollState = rememberScrollState()

    // Show error if any
    playerError?.let { error ->
        LaunchedEffect(error) {
            // Show error in a snackbar or dialog
            Log.e("MusicDAOTorrent", "Player error: $error")
        }
    }

    albumState?.let { album ->
        LaunchedEffect(
            key1 = playerViewModel,
            block = {
                viewModel.torrentState.collect {
                    val current = playerViewModel.playingTrack.value ?: return@collect
                    val downloadingTracks =
                        viewModel.torrentState.value?.downloadingTracks ?: return@collect
                    val isPlaying = playerViewModel.exoPlayer.isPlaying
                    val targetTrack =
                        downloadingTracks.find { it.file.name == current.file?.name }
                            ?: return@collect

                    // Only auto-play if enough progress and not already playing
                    if (!isPlaying && targetTrack.progress > 20 && targetTrack.progress < 99) {
                        play(targetTrack, album.cover)
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Header(album, navController)
            TabRow(selectedTabIndex = state) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = state == index,
                        onClick = { state = index },
                        text = { Text(title) }
                    )
                }
            }
            when (state) {
                0 -> {
                    if (album.songs != null && album.songs.isNotEmpty()) {
                        album.songs.forEach { song ->
                            ListItem(
                                text = { Text(song.title) },
                                secondaryText = { Text(album.artist) },
                                trailing = {
                                    IconButton(onClick = { play(song, album.cover) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayArrow,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        if (torrentStatus != null && canDownload) {
                            val downloadingTracks = torrentStatus?.downloadingTracks
                            downloadingTracks?.map {
                                ListItem(
                                    text = { Text(it.title) },
                                    secondaryText = {
                                        Column {
                                            Text(album.artist, modifier = Modifier.padding(bottom = 5.dp))
                                            LinearProgressIndicator(progress = it.progress.toFloat() / 100)
                                            if (it.progress < 20) {
                                                Text(
                                                    "Waiting for enough data to play...",
                                                    style = MaterialTheme.typography.caption,
                                                    color = MaterialTheme.colors.error
                                                )
                                            }
                                        }
                                    },
                                    trailing = {
                                        IconButton(
                                            onClick = { play(it, album.cover) },
                                            enabled = it.progress >= 20
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PlayArrow,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                )
                            }
                            if (downloadingTracks == null || downloadingTracks.isEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val current = torrentStatus
                    if (current != null) {
                        TorrentStatusScreen(current)
                    } else {
                        Text("Could not find torrent.")
                    }
                }
            }
        }
    }
}

@Composable
fun Header(
    album: Album,
    navController: NavController
) {
    Column(modifier = Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp)) {
        Text(
            album.title,
            style = MaterialTheme.typography.h6.merge(SpanStyle(fontWeight = FontWeight.ExtraBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Text(
            album.artist,
            style = MaterialTheme.typography.body2.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Text(
            "UUID",
            style = MaterialTheme.typography.body2.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Text(
            album.id,
            style = MaterialTheme.typography.body2.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Text(
            "Artist Public Key",
            style = MaterialTheme.typography.body2.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Text(
            album.publisher,
            style = MaterialTheme.typography.body2.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            modifier = Modifier.padding(bottom = 5.dp)
        )

        Text(
            "Album - ${dateToShortString(album.releaseDate.toString())}",
            style =
                MaterialTheme.typography.body2.merge(
                    SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.Gray)
                ),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row {
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null
                    )
                }
                IconButton(
                    onClick = {
                        navController.navigate(
                            Screen.Profile.createRoute(publicKey = album.publisher)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                }
                IconButton(
                    onClick = {
                        navController.navigate(
                            Screen.Donate.createRoute(publicKey = album.publisher)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null
                    )
                }

                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart)) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                navController.navigate(
                                    Screen.Profile.createRoute(publicKey = album.publisher)
                                )
                            }
                        ) {
                            Text("View Artist")
                        }
                        DropdownMenuItem(
                            onClick = {
                                navController.navigate(
                                    Screen.Donate.createRoute(publicKey = album.publisher)
                                )
                            }
                        ) {
                            Text("Donate")
                        }
                        DropdownMenuItem(onClick = { }) {
                            Text("View Meta-data")
                        }
                    }
                }
            }
            IconButton(onClick = { /*TODO*/ }) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}

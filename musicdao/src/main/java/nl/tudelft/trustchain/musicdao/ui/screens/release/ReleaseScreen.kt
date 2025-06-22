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
import androidx.compose.ui.text.style.TextAlign
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
import android.util.Log
import nl.tudelft.trustchain.musicdao.core.util.ListenCounter

@ExperimentalMaterialApi
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

    val playingTrack = playerViewModel.playingTrack.collectAsState()

    // Audio Player
    val context = LocalContext.current

    fun play(
        track: Song,
        album: Album
    ) {
        ListenCounter.increment(context, album.publisher)
        Log.d("Counter", "Counter: ${ListenCounter.getCount(context, album.publisher)}")
        playerViewModel.playDownloadedTrack(track, album.cover)
    }

    fun play(
        track: DownloadingTrack,
        album: Album
    ) {
        ListenCounter.increment(context, album.publisher)
        Log.d("Counter", "Counter: ${ListenCounter.getCount(context, album.publisher)}")
        playerViewModel.playDownloadingTrack(
            Song(
                file = track.file,
                artist = track.artist,
                title = track.title
            ),
            context,
            album.cover
        )
    }

    val scrollState = rememberScrollState()

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

                    if (!isPlaying && targetTrack.progress > 20 && targetTrack.progress < 99) {
                        play(targetTrack, album)
                    }
                }
            }
        )

        Column(
            modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(bottom = 150.dp)
        ) {
            TabRow(selectedTabIndex = state) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        onClick = { state = index },
                        selected = (index == state),
                        text = { Text(title) }
                    )
                }
            }
            if (state == 0) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 20.dp)
                ) {
                    ReleaseCover(
                        file = album.cover,
                        modifier =
                            Modifier
                                .height(200.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10))
                                .background(Color.DarkGray)
                                .shadow(10.dp)
                                .align(Alignment.CenterHorizontally)
                    )
                }
                Header(album, navController = navController)
                if (album.songs != null && album.songs.isNotEmpty()) {
                    val files = album.songs
                    files.map {
                        val isPlayingModifier =
                            playingTrack.value?.let { current ->
                                if (it.title == current.title) {
                                    MaterialTheme.colors.primary
                                } else {
                                    MaterialTheme.colors.onBackground
                                }
                            } ?: MaterialTheme.colors.onBackground

                        ListItem(
                            text = { Text(it.title, color = isPlayingModifier, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryText = { Text(it.artist, color = isPlayingModifier) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { play(it, album) }
                        )
                    }
                } else {
                    if (torrentStatus != null) {
                        val downloadingTracks = torrentStatus?.downloadingTracks
                        downloadingTracks?.map {
                            ListItem(
                                text = { Text(it.title) },
                                secondaryText = {
                                    Column {
                                        Text(album.artist, modifier = Modifier.padding(bottom = 5.dp))
                                        LinearProgressIndicator(progress = it.progress.toFloat() / 100)
                                    }
                                },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = null
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        play(it, album)
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
            if (state == 1) {
                val current = torrentStatus
                val accessReason = viewModel.accessReason.collectAsState().value

                when {
                    current != null -> {
                        TorrentStatusScreen(current)
                    }
                    accessReason == ReleaseScreenViewModel.AccessReason.DOWNLOADING -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Initializing torrent...",
                                style = MaterialTheme.typography.h6
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text =
                                    when (accessReason) {
                                        ReleaseScreenViewModel.AccessReason.RESTRICTED -> "This release is currently restricted"
                                        ReleaseScreenViewModel.AccessReason.WAITING_PERIOD -> "This release will be available in 7 days"
                                        ReleaseScreenViewModel.AccessReason.NO_MAGNET -> "This release is not available"
                                        ReleaseScreenViewModel.AccessReason.DOWNLOAD_ERROR -> "Error downloading release"
                                        null -> "Release not available for download"
                                        else -> "Loading..."
                                    },
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.error
                            )
                            if (accessReason == ReleaseScreenViewModel.AccessReason.RESTRICTED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Upgrade to Pro to access this release immediately, or wait for the release period to end",
                                    style = MaterialTheme.typography.body2,
                                    textAlign = TextAlign.Center
                                )
                            } else if (accessReason == ReleaseScreenViewModel.AccessReason.WAITING_PERIOD) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Upgrade to Pro to access this release immediately",
                                    style = MaterialTheme.typography.body2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
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
                // Direct Donations are not allowed at the moment!
                // IconButton(
                //     onClick = {
                //         navController.navigate(
                //             Screen.Donate.createRoute(publicKey = album.publisher)
                //         )
                //     }
                // ) {
                //     Icon(
                //         imageVector = Icons.Default.ShoppingCart,
                //         contentDescription = null
                //     )
                // }

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
                        // Direct Donations are not allowed at the moment!
                        // DropdownMenuItem(
                        //     onClick = {
                        //         navController.navigate(
                        //             Screen.Donate.createRoute(publicKey = album.publisher)
                        //         )
                        //     }
                        // ) {
                        //     Text("Donate")
                        // }
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

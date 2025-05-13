@file:Suppress("DEPRECATION")

package nl.tudelft.trustchain.musicdao.ui.components.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import nl.tudelft.trustchain.musicdao.core.repositories.model.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class PlayerViewModel(context: Context) : ViewModel() {
    private val _playingTrack: MutableStateFlow<Song?> = MutableStateFlow(null)
    val playingTrack: StateFlow<Song?> = _playingTrack

    private val _coverFile: MutableStateFlow<File?> = MutableStateFlow(null)
    val coverFile: StateFlow<File?> = _coverFile

    private val _error: MutableStateFlow<String?> = MutableStateFlow(null)
    val error: StateFlow<String?> = _error

    val exoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    _error.value = "Playback error: ${error.message}"
                    Log.e("MusicDAOTorrent", "Player error: ${error.message}")
                }
            })
        }
    }

    private fun buildMediaSource(
        uri: Uri,
        context: Context
    ): MediaSource {
        @Suppress("DEPRECATION")
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSourceFactory(context, "musicdao-audioplayer")
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    fun playDownloadedTrack(
        track: Song,
        cover: File? = null
    ) {
        if (!(track.file!!.exists())) {
            _error.value = "File does not exist: ${track.file}"
            Log.e("MusicDAOTorrent", "File does not exist: ${track.file}")
            return
        }

        try {
            _playingTrack.value = track
            _coverFile.value = cover
            val mediaItem = MediaItem.fromUri(Uri.fromFile(track.file))
            Log.d("MusicDAOTorrent", "Playing downloaded track: ${track.file}")
            exoPlayer.playWhenReady = true
            exoPlayer.seekTo(0, 0)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            _error.value = "Error playing track: ${e.message}"
            Log.e("MusicDAOTorrent", "Error playing track", e)
        }
    }

    fun playDownloadingTrack(
        track: Song,
        context: Context,
        cover: File? = null
    ) {
        if (!track.file!!.exists()) {
            _error.value = "File does not exist: ${track.file}"
            Log.e("MusicDAOTorrent", "File does not exist: ${track.file}")
            return
        }

        try {
            _playingTrack.value = track
            _coverFile.value = cover
            val mediaSource = buildMediaSource(Uri.fromFile(track.file), context)
            Log.d("MusicDAOTorrent", "Playing downloading track: ${track.file}")
            exoPlayer.playWhenReady = true
            exoPlayer.seekTo(0, 0)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            _error.value = "Error playing track: ${e.message}"
            Log.e("MusicDAOTorrent", "Error playing track", e)
        }
    }

    fun release() {
        try {
            exoPlayer.release()
            exoPlayer.stop()
        } catch (e: Exception) {
            Log.e("MusicDAOTorrent", "Error releasing player", e)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        context
                    ) as T
                }
            }
    }
}

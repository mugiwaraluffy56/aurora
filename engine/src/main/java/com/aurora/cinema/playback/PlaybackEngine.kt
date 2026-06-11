package com.aurora.cinema.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import com.aurora.cinema.library.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackEngine(
    private val context: Context,
    val player: ExoPlayer,
    private val progressStore: PlaybackProgressStore,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    override val mediaPlayer: Player = player

    private var selectedVideo: VideoItem? = null
    private var subtitleSettings = SubtitleSettings()
    private var latestSubtitleText = ""
    private var latestTextTracks: List<TimedTextTrack> = emptyList()

    init {
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    syncState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncState()
                    if (!isPlaying) saveProgress()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    mutableState.value = mutableState.value.copy(
                        error = PlaybackError(
                            message = error.localizedMessage ?: "Playback failed",
                            causeName = error.errorCodeName,
                        ),
                    )
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val displaySize = VideoDisplaySizeCalculator.calculate(
                        width = videoSize.width,
                        height = videoSize.height,
                        pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
                    )
                    mutableState.value = mutableState.value.copy(
                        videoWidth = displaySize.width,
                        videoHeight = displaySize.height,
                    )
                }

                override fun onTracksChanged(tracks: Tracks) {
                    latestTextTracks = tracks.groups
                        .filter { group -> group.type == C.TRACK_TYPE_TEXT }
                        .flatMapIndexed { groupIndex, group ->
                            List(group.length) { trackIndex ->
                                val format = group.getTrackFormat(trackIndex)
                                TimedTextTrack(
                                    id = "$groupIndex:$trackIndex",
                                    label = format.label ?: format.language ?: "Subtitle ${trackIndex + 1}",
                                    language = format.language,
                                )
                            }
                        }
                    syncState()
                }

                override fun onCues(cueGroup: CueGroup) {
                    latestSubtitleText = if (subtitleSettings.enabled) {
                        cueGroup.cues.joinToString(separator = "\n") { cue ->
                            cue.text?.toString().orEmpty()
                        }.trim()
                    } else {
                        ""
                    }
                    syncState()
                }
            },
        )
        scope.launch {
            var ticksSinceSave = 0
            while (true) {
                delay(1_000L)
                if (selectedVideo != null) {
                    syncState()
                    if (player.isPlaying) {
                        ticksSinceSave += 1
                        if (ticksSinceSave >= 5) {
                            saveProgress()
                            ticksSinceSave = 0
                        }
                    } else {
                        ticksSinceSave = 0
                    }
                }
            }
        }
    }

    override fun select(video: VideoItem) {
        saveProgress()
        selectedVideo = video
        mutableState.value = PlaybackState(selectedVideo = video)
        ensureServiceStarted()

        scope.launch {
            val resumePosition = progressStore.loadPosition(video.id)
            player.setMediaItem(MediaItem.fromUri(Uri.parse(video.uri)))
            player.prepare()
            if (resumePosition > 0L) {
                player.seekTo(resumePosition)
            }
            syncState()
        }
    }

    override fun play() {
        ensureServiceStarted()
        player.play()
        syncState()
    }

    override fun pause() {
        player.pause()
        saveProgress()
        syncState()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        syncState()
    }

    override fun stop() {
        saveProgress()
        player.stop()
        selectedVideo = null
        mutableState.value = PlaybackState()
    }

    override fun saveProgress() {
        val video = selectedVideo ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        scope.launch {
            progressStore.savePosition(video.id, position, duration)
        }
    }

    override fun setVideoSurface(surface: Surface?) {
        player.setVideoSurface(surface)
    }

    override fun setSubtitleSettings(settings: SubtitleSettings) {
        subtitleSettings = settings.clamped()
        if (!subtitleSettings.enabled) {
            latestSubtitleText = ""
        }
        syncState()
    }

    private fun syncState() {
        val video = selectedVideo
        mutableState.value = mutableState.value.copy(
            selectedVideo = video,
            isReady = player.playbackState == Player.STATE_READY,
            isPlaying = player.isPlaying,
            durationMs = player.duration.takeIf { it > 0L } ?: video?.durationMs ?: 0L,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            timedTextTracks = latestTextTracks,
            subtitleText = latestSubtitleText,
        )
    }

    private fun ensureServiceStarted() {
        runCatching {
            context.startService(Intent(context, AuroraPlaybackService::class.java))
        }
    }
}

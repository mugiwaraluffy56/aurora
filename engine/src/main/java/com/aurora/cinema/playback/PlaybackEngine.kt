package com.aurora.cinema.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlaybackException
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
    private var currentVideoSurface: Surface? = null
    private var recoveryAttemptedForVideoId: Long? = null
    private var recoverWhenSurfaceArrives = false
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

                override fun onPlayerError(error: PlaybackException) {
                    val recoveryQueued = maybeRecoverFromVideoRendererError(error)
                    mutableState.value = mutableState.value.copy(
                        error = PlaybackError(
                            message = buildPlaybackErrorMessage(error, recoveryQueued),
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
        recoveryAttemptedForVideoId = null
        recoverWhenSurfaceArrives = false
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
        recoveryAttemptedForVideoId = null
        recoverWhenSurfaceArrives = false
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
        currentVideoSurface = surface
        player.setVideoSurface(surface)
        if (surface != null && recoverWhenSurfaceArrives) {
            recoverWhenSurfaceArrives = false
            recoverVideoRenderer()
        }
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

    private fun maybeRecoverFromVideoRendererError(error: PlaybackException): Boolean {
        if (!isVideoRendererError(error)) return false
        val video = selectedVideo ?: return false
        if (recoveryAttemptedForVideoId == video.id) return false

        recoveryAttemptedForVideoId = video.id
        val surface = currentVideoSurface
        if (surface == null || !surface.isValid) {
            recoverWhenSurfaceArrives = true
            return true
        }

        recoverVideoRenderer()
        return true
    }

    private fun recoverVideoRenderer() {
        val video = selectedVideo ?: return
        val resumePosition = player.currentPosition
            .takeIf { it > 0L }
            ?: mutableState.value.positionMs
                .takeIf { it > 0L }
            ?: video.playbackPositionMs
        val shouldResume = player.playWhenReady || mutableState.value.isPlaying

        scope.launch {
            runCatching {
                player.clearVideoSurface()
                currentVideoSurface?.takeIf { it.isValid }?.let(player::setVideoSurface)
                player.setMediaItem(MediaItem.fromUri(Uri.parse(video.uri)), resumePosition)
                player.prepare()
                player.playWhenReady = shouldResume
                mutableState.value = mutableState.value.copy(error = null)
                syncState()
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    error = PlaybackError(
                        message = "Playback recovery failed: ${failure.localizedMessage ?: failure.javaClass.simpleName}",
                        causeName = failure.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    private fun isVideoRendererError(error: PlaybackException): Boolean {
        val exoError = error as? ExoPlaybackException ?: return false
        if (exoError.type != ExoPlaybackException.TYPE_RENDERER) return false
        val rendererName = exoError.rendererName.orEmpty()
        val sampleMimeType = exoError.rendererFormat?.sampleMimeType.orEmpty()
        return rendererName.contains("Video", ignoreCase = true) || sampleMimeType.startsWith("video/")
    }

    private fun buildPlaybackErrorMessage(error: PlaybackException, recoveryQueued: Boolean): String {
        val base = error.localizedMessage ?: "Playback failed"
        return if (recoveryQueued) {
            "$base\nRetrying video renderer with a fresh surface."
        } else {
            base
        }
    }
}

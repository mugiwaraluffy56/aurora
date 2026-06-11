package com.aurora.cinema.input

import android.view.KeyEvent
import com.aurora.cinema.playback.PlayerController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidHardwareInputController(
    private val playerController: PlayerController,
) : HardwareInputController {
    private val mutableActions = MutableSharedFlow<InputAction>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var settings = HardwareInputSettings()

    override val actions: SharedFlow<InputAction> = mutableActions.asSharedFlow()

    override fun updateSettings(settings: HardwareInputSettings) {
        this.settings = settings
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val input = event.toPhysicalInput() ?: return false
        val action = settings.actionFor(input)
        val consume = action != InputAction.None &&
            (input !in volumeInputs || settings.consumeVolumeKeys)
        if (!consume) return false

        perform(action)
        mutableActions.tryEmit(action)
        return true
    }

    private fun perform(action: InputAction) {
        val state = playerController.state.value
        when (action) {
            InputAction.PlayPause -> if (state.isPlaying) playerController.pause() else playerController.play()
            InputAction.SeekBack -> playerController.seekTo((state.positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
            InputAction.SeekForward -> playerController.seekTo(state.positionMs + SEEK_STEP_MS)
            InputAction.Stop -> playerController.stop()
            InputAction.Recenter,
            InputAction.VolumeUp,
            InputAction.VolumeDown,
            InputAction.None,
            -> Unit
        }
    }

    private fun KeyEvent.toPhysicalInput(): PhysicalInput? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            -> PhysicalInput.Center
            KeyEvent.KEYCODE_SPACE -> PhysicalInput.Space
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> PhysicalInput.Enter
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> PhysicalInput.MediaPlayPause
            KeyEvent.KEYCODE_MEDIA_STOP -> PhysicalInput.MediaStop
            KeyEvent.KEYCODE_DPAD_LEFT -> PhysicalInput.DpadLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> PhysicalInput.DpadRight
            KeyEvent.KEYCODE_BUTTON_L1 -> PhysicalInput.ButtonL1
            KeyEvent.KEYCODE_BUTTON_R1 -> PhysicalInput.ButtonR1
            KeyEvent.KEYCODE_BUTTON_X -> PhysicalInput.ButtonX
            KeyEvent.KEYCODE_VOLUME_UP -> PhysicalInput.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> PhysicalInput.VolumeDown
            KeyEvent.KEYCODE_BACK -> PhysicalInput.Back
            else -> null
        }
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
        val volumeInputs = setOf(PhysicalInput.VolumeUp, PhysicalInput.VolumeDown)
    }
}

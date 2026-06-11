package com.aurora.cinema.input

import kotlinx.coroutines.flow.SharedFlow

enum class InputAction(
    val label: String,
) {
    PlayPause("Play / pause"),
    SeekBack("Seek back 10 seconds"),
    SeekForward("Seek forward 10 seconds"),
    Stop("Stop playback"),
    Recenter("Recenter view"),
    VolumeUp("System volume up"),
    VolumeDown("System volume down"),
    None("Unassigned"),
}

enum class PhysicalInput(
    val label: String,
) {
    Center("D-pad center / controller A"),
    Space("Space"),
    Enter("Enter"),
    MediaPlayPause("Media play/pause"),
    MediaStop("Media stop"),
    DpadLeft("D-pad left"),
    DpadRight("D-pad right"),
    ButtonL1("Controller L1"),
    ButtonR1("Controller R1"),
    ButtonX("Controller X"),
    VolumeUp("Volume up"),
    VolumeDown("Volume down"),
    Back("Back"),
}

data class InputBinding(
    val input: PhysicalInput,
    val action: InputAction,
)

data class HardwareInputSettings(
    val bindings: List<InputBinding> = defaultBindings,
    val consumeVolumeKeys: Boolean = false,
) {
    fun actionFor(input: PhysicalInput): InputAction {
        return bindings.lastOrNull { it.input == input }?.action ?: InputAction.None
    }

    companion object {
        val defaultBindings = listOf(
            InputBinding(PhysicalInput.Center, InputAction.PlayPause),
            InputBinding(PhysicalInput.Space, InputAction.PlayPause),
            InputBinding(PhysicalInput.Enter, InputAction.PlayPause),
            InputBinding(PhysicalInput.MediaPlayPause, InputAction.PlayPause),
            InputBinding(PhysicalInput.DpadLeft, InputAction.SeekBack),
            InputBinding(PhysicalInput.ButtonL1, InputAction.SeekBack),
            InputBinding(PhysicalInput.DpadRight, InputAction.SeekForward),
            InputBinding(PhysicalInput.ButtonR1, InputAction.SeekForward),
            InputBinding(PhysicalInput.ButtonX, InputAction.Recenter),
            InputBinding(PhysicalInput.MediaStop, InputAction.Stop),
            InputBinding(PhysicalInput.Back, InputAction.Recenter),
            InputBinding(PhysicalInput.VolumeUp, InputAction.VolumeUp),
            InputBinding(PhysicalInput.VolumeDown, InputAction.VolumeDown),
        )
    }
}

interface HardwareInputController {
    val actions: SharedFlow<InputAction>

    fun updateSettings(settings: HardwareInputSettings)
}

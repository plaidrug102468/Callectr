package app.callectr.recorder.services.recording

internal object LiveRecordControlsPolicy {
    enum class Mode { STARTING, STANDBY, ACTIVE, PAUSED }
    enum class Control { RECORD, PAUSE, RESUME, FLAG, STOP }

    fun controls(mode: Mode): List<Control> = when (mode) {
        Mode.STARTING -> emptyList()
        Mode.STANDBY -> listOf(Control.RECORD)
        Mode.ACTIVE -> listOf(Control.PAUSE, Control.FLAG, Control.STOP)
        Mode.PAUSED -> listOf(Control.RESUME, Control.FLAG, Control.STOP)
    }
}

package app.callectr.recorder.services.recording

import app.callectr.recorder.services.recording.LiveRecordControlsPolicy.Control
import app.callectr.recorder.services.recording.LiveRecordControlsPolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRecordPromptTest {

    @Test
    fun `manual incoming or outgoing standby exposes Record`() {
        val controls = LiveRecordControlsPolicy.controls(Mode.STANDBY)
        assertEquals(listOf(Control.RECORD), controls)
    }

    @Test
    fun `starting capture has no stale Record action`() {
        val controls = LiveRecordControlsPolicy.controls(Mode.STARTING)
        assertTrue(controls.isEmpty())
    }

    @Test
    fun `active recording replaces Record with Pause Mark Stop`() {
        val controls = LiveRecordControlsPolicy.controls(Mode.ACTIVE)
        assertEquals(listOf(Control.PAUSE, Control.FLAG, Control.STOP), controls)
        assertFalse(controls.contains(Control.RECORD))
    }

    @Test
    fun `paused recording replaces Record with Resume Mark Stop`() {
        val controls = LiveRecordControlsPolicy.controls(Mode.PAUSED)
        assertEquals(listOf(Control.RESUME, Control.FLAG, Control.STOP), controls)
        assertFalse(controls.contains(Control.RECORD))
    }
}

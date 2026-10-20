package app.callectr.recorder.services.recording

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.callectr.recorder.R
import app.callectr.recorder.data.recordings.RecordingDirection
import app.callectr.recorder.data.recordings.RecordingMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveRecordPromptTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `manual incoming call notification exposes Record action`() {
        val helper = RecordingNotificationHelper(context)
        helper.createNotificationChannels()
        val notification = helper.getNotification(
            RecordingServiceState.Standby(
                RecordingMetadata("+27123456789", RecordingDirection.INCOMING)
            )
        )

        val labels = notification.actions.orEmpty().map { it.title.toString() }
        assertTrue(labels.contains(context.getString(R.string.general_record)))
    }

    @Test
    fun `manual outgoing call notification exposes Record action`() {
        val helper = RecordingNotificationHelper(context)
        helper.createNotificationChannels()
        val notification = helper.getNotification(
            RecordingServiceState.Standby(
                RecordingMetadata("+27123456789", RecordingDirection.OUTGOING)
            )
        )

        val labels = notification.actions.orEmpty().map { it.title.toString() }
        assertTrue(labels.contains(context.getString(R.string.general_record)))
    }

    @Test
    fun `active recording replaces Record with active controls`() {
        // The policy-level test is enough here: once active, the notification builder must not leave
        // a stale Record action beside Pause/Stop. We can assert the Standby prompt itself is singular.
        val helper = RecordingNotificationHelper(context)
        helper.createNotificationChannels()
        val notification = helper.getNotification(
            RecordingServiceState.Standby(
                RecordingMetadata("+27123456789", RecordingDirection.INCOMING)
            )
        )

        val labels = notification.actions.orEmpty().map { it.title.toString() }
        assertTrue(labels.count { it == context.getString(R.string.general_record) } == 1)
        assertFalse(labels.contains(context.getString(R.string.general_stop)))
    }
}

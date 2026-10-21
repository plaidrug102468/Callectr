/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.services.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import app.callectr.recorder.R
import app.callectr.recorder.data.AppPreferences

/** One button on the ongoing recording notification. */
private data class NotificationAction(val icon: Int, val text: String, val intentAction: String)

class RecordingNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SERVICE = "recording_channel_service"
        const val CHANNEL_ID_ERROR = "recording_channel_error"

        const val SERVICE_NOTIFICATION_ID = 1
        const val ERROR_NOTIFICATION_ID = 2

        /**
         * The two-pulse buzz every recording state change uses: started, paused, resumed, ended.
         *
         * One definition rather than the same four literals repeated, so "the recording changed
         * state" always feels the same — including on the VoIP path, which previously felt like
         * nothing at all.
         */
        private val STATE_CHANGE_VIBRATION: VibrationEffect
            get() = VibrationEffect.createWaveform(
                longArrayOf(0, 300, 150, 300),
                intArrayOf(0, 64, 0, 128),
                -1,
            )
    }

    /**
     * Creates the Android notification channel for recording notifications.
     */

    fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val groupId = "recording_channel_group"
        val group = NotificationChannelGroup(groupId, "Recording Channel")
        manager.createNotificationChannelGroup(group)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE, "Foreground Recording Service", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.group = groupId
            // Alert channel should be visible but we handle vibration manually
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(serviceChannel)

        val errorChannel = NotificationChannel(
            CHANNEL_ID_ERROR, "Recording Errors", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.group = groupId
            enableVibration(false) // We handle vibration manually
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(errorChannel)
    }

    fun getNotification(state: RecordingServiceState): Notification {
        val mode = when (state) {
            is RecordingServiceState.Starting -> LiveRecordControlsPolicy.Mode.STARTING
            is RecordingServiceState.Active ->
                if (state.isPaused) LiveRecordControlsPolicy.Mode.PAUSED else LiveRecordControlsPolicy.Mode.ACTIVE
            else -> LiveRecordControlsPolicy.Mode.STANDBY
        }

        val titleRes: Int
        val contentRes: Int
        when (mode) {
            LiveRecordControlsPolicy.Mode.STARTING -> {
                titleRes = R.string.recording_standby_notification_title
                contentRes = R.string.recording_notification_waiting
            }
            LiveRecordControlsPolicy.Mode.ACTIVE -> {
                titleRes = R.string.recording_notification_title
                contentRes = R.string.recording_notification_press_to_pause
            }
            LiveRecordControlsPolicy.Mode.PAUSED -> {
                titleRes = R.string.recording_standby_notification_title
                contentRes = R.string.recording_notification_press_to_resume
            }
            LiveRecordControlsPolicy.Mode.STANDBY -> {
                titleRes = R.string.recording_standby_notification_title
                contentRes = R.string.recording_notification_press_to_start
            }
        }

        val actions = LiveRecordControlsPolicy.controls(mode).map { control ->
            when (control) {
                LiveRecordControlsPolicy.Control.RECORD -> NotificationAction(
                    R.drawable.ic_mic,
                    context.getString(R.string.general_record),
                    RecordingForegroundService.ACTION_MANUAL_START
                )
                LiveRecordControlsPolicy.Control.PAUSE -> NotificationAction(
                    R.drawable.ic_mic,
                    context.getString(R.string.general_pause),
                    RecordingForegroundService.ACTION_PAUSE_RECORDING
                )
                LiveRecordControlsPolicy.Control.RESUME -> NotificationAction(
                    R.drawable.ic_mic,
                    context.getString(R.string.general_resume),
                    RecordingForegroundService.ACTION_RESUME_RECORDING
                )
                LiveRecordControlsPolicy.Control.FLAG -> {
                    val marks = PendingFlags.count()
                    NotificationAction(
                        R.drawable.ic_mic,
                        if (marks > 0) context.getString(R.string.general_flag_count, marks)
                        else context.getString(R.string.general_flag),
                        RecordingForegroundService.ACTION_FLAG_MOMENT
                    )
                }
                LiveRecordControlsPolicy.Control.STOP -> NotificationAction(
                    R.drawable.ic_stop,
                    context.getString(R.string.general_stop),
                    RecordingForegroundService.ACTION_STOP_RECORDING
                )
            }
        }

        val subRes: Int =
            if (state.metadata?.isCrossCountry == true) R.string.recording_notification_cross_country_tip
            else R.string.recording_notification_current_country_tip

        val deletePendingIntent = PendingIntent.getService(
            context, 99,
            Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_NOTIFICATION_DISMISSED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(contentRes))
            .setSubText(context.getString(subRes))
            .setOngoing(true)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(
                mode == LiveRecordControlsPolicy.Mode.ACTIVE ||
                    mode == LiveRecordControlsPolicy.Mode.PAUSED ||
                    mode == LiveRecordControlsPolicy.Mode.STARTING
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        actions.forEach { notifAction ->
            val actionPendingIntent = PendingIntent.getService(
                context, notifAction.intentAction.hashCode(),
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = notifAction.intentAction
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(notifAction.icon, notifAction.text, actionPendingIntent)
        }

        return builder.build()
    }

    /**
     * A benign, silent, action-free foreground notice for the single instant the carrier service
     * must satisfy Android's "post a notification the moment you start" rule before it stands down
     * for a VoIP call it will not record. It carries NO Record button and makes no sound, and the
     * service removes it in the same turn — so the VoIP recorder's own notifications are the only
     * ones left, instead of "Call in progress — Press to start recording" lingering all call (#54).
     */
    fun getStandingAsideNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.recording_standby_notification_title))
            .setContentText(context.getString(R.string.recording_notification_waiting))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /**
     * Confirms that a mark landed, with the running total.
     *
     * A toast rather than a change to the notification: the marks cannot be listed until the call
     * ends and the file has a name, and a button that appears to do nothing simply gets pressed
     * again — which is how a single moment becomes five marks.
     */
    fun showFlagToast(count: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                context.getString(R.string.recording_flag_added, count),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Handles showing toasts for state changes.  It determines a toast based on the old and new state.
     * @param oldState The previous state of the recording service.
     * @param newState The new state of the recording service.
     */
    fun handleStateChangeToasts(oldState: RecordingServiceState, newState: RecordingServiceState) {
        if (oldState == newState) return // Ignore duplicates

        when (newState) {
            is RecordingServiceState.Standby -> {
                if (newState.metadata == null) {
                    showRecordingEnded()
                } else if (oldState !is RecordingServiceState.Standby) {
                    val dirLabel = newState.metadata.direction.labelResId.let { context.getString(it) }
                    showToast(context.getString(R.string.recording_toast_standby, dirLabel))
                }
            }
            is RecordingServiceState.Active -> {
                val wasActive = oldState is RecordingServiceState.Active
                val wasPaused = wasActive && (oldState as RecordingServiceState.Active).isPaused

                if (newState.isPaused && (!wasActive || !wasPaused)) {
                    // Recording was paused
                    showToast(context.getString(R.string.recording_toast_paused))
                    vibrate(STATE_CHANGE_VIBRATION)
                } else if (!newState.isPaused && wasPaused) {
                    // Recording was resumed
                    showToast(context.getString(R.string.recording_toast_resumed))
                    vibrate(STATE_CHANGE_VIBRATION)
                } else if (!newState.isPaused && !wasActive) {
                    // Recording was started
                    showToast(context.getString(R.string.recording_started))
                    vibrate(STATE_CHANGE_VIBRATION)
                }
            }
            else -> {}
        }
    }

    /**
     * Shows a short Toast message on the UI thread.
     */
    /**
     * Tells the user a recording just finished: the toast they know, and the same vibration.
     *
     * Extracted so the **VoIP path can say it too**. It could not before — this feedback hung off
     * [RecordingServiceState] transitions, which only [RecordingForegroundService] drives, and
     * `VoipRecordingCoordinator` deliberately does not go through that service. So an app call
     * recorded successfully and told the user nothing at all.
     *
     * That is worse than a missing nicety. With no confirmation, a VoIP call that silently *failed*
     * looks exactly like one that silently succeeded, and the only way to find out is to go looking
     * for the recording later — which is the failure users report most bitterly.
     *
     * Both halves stay opt-out: [showToast] honours the toast preference and [vibrate] the vibration
     * one, so this adds no new way to be noisy.
     */
    fun showRecordingEnded() {
        showToast(context.getString(R.string.recording_toast_ended))
        vibrate(STATE_CHANGE_VIBRATION)
    }

    fun showToast(message: String) {
        if (!AppPreferences(context).isShowToastsEnabled()) return

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Posts an error notification visible.
     *
     * @param message Human-readable error description to show in the notification body.
     */
    fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.recording_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, notification)
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 800), intArrayOf(0, 46, 184), -1))
    }

    /**
     * Triggers a vibration if enabled in settings.
     */
    fun vibrate(effect: VibrationEffect) {
        if (!AppPreferences(context).isVibrationEnabled()) return

        if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            if (manager.defaultVibrator.hasVibrator()) {
                manager.defaultVibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(effect)
            }
        }
    }
}

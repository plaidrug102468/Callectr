/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.data

/**
 * The user's master recording On/Off switch, layered ON TOP of the per-type (carrier / VoIP) settings so
 * that turning it back on restores those exactly as they were, never clobbers them.
 *
 * - [ACTIVE]  — on: the recorder is armed and (in standalone) the daemon is kept warm.
 * - [STOPPED] — off: the daemon and the keep-alive service are stopped (so the persistent notification
 *               goes away) and the Shizuku user service is unbound; turning it back on cold-starts the
 *               recorder.
 *
 * [STOPPED] **survives a reboot** — the boot path must honour the stored state and not resurrect the
 * recorder until the user turns it back on.
 *
 * (A warm "pause" state was tried and dropped: entering it was indistinguishable from Stop to the user,
 * its only real difference — an instant, notification-kept resume — was too narrow to justify a third
 * state, and keeping a persistent notification contradicted the "stop the app" request this serves.)
 */
enum class RecordingState(val key: String) {
    ACTIVE("active"),
    STOPPED("stopped");

    /** Whether a call should be recorded right now. Only [ACTIVE] records. */
    val isRecordingAllowed: Boolean get() = this == ACTIVE

    companion object {
        /** Falls back to [ACTIVE] for anything unknown — including the retired "paused" key. */
        fun fromKey(k: String?): RecordingState = entries.firstOrNull { it.key == k } ?: ACTIVE
    }
}

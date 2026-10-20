/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.data

/**
 * Which captured channel carries the far party, once it is known.
 *
 * **It cannot be hardcoded.** Android documents `MediaRecorder.AudioSource.VOICE_CALL` as "uplink +
 * downlink" and never says which channel is which — it is an OEM decision, and differs between
 * devices. So the app learns it from the ringback on an outgoing call, where only the far channel
 * carries the network's tone, and reuses the answer for every later call including incoming ones,
 * which have no ringback phase of their own.
 *
 * **[UNKNOWN] is the safe answer and the default.** Without a confident mapping the transcript shows
 * neutral "Speaker A" and "Speaker B" — which is honest. A wrong mapping shows the user's own words
 * attributed to the other person, on a record of a real conversation, and that is a far worse defect
 * than showing no names at all.
 *
 * Carrier calls only. `VoipCaptureSession` builds its own stereo with the near party on the left and
 * the far party on the right, so app calls are correct by construction and never consult this.
 */
enum class ChannelMap(val key: String) {

    /** Not learned yet, or learned and not trusted. Show neutral labels. */
    UNKNOWN("unknown"),

    /** Channel A (left, index 0) is the far party; B is the user. */
    A_IS_FAR("a_far"),

    /** Channel B (right, index 1) is the far party; A is the user. */
    B_IS_FAR("b_far");

    /** True when [channelIndex] is the person on the other end. Meaningless while [UNKNOWN]. */
    fun isFar(channelIndex: Int): Boolean = when (this) {
        A_IS_FAR -> channelIndex == 0
        B_IS_FAR -> channelIndex == 1
        UNKNOWN -> false
    }

    /**
     * The near party's channel letter — the side that is *not* the far party — or null while
     * [UNKNOWN]. The letters match `SpeakerChannel`'s keys ("A" is index 0, "B" is index 1).
     *
     * Null is deliberate, not a gap: it says "we have not confidently worked out which channel is
     * yours", the same honest position the neutral "Speaker A / Speaker B" labels take. A reader that
     * gets null must not assume a side. Used to fill the details file's `near_channel` for a carrier
     * call (issue #50); app calls do not consult this (see the class note).
     */
    fun nearChannelKey(): String? = when (this) {
        A_IS_FAR -> "B"
        B_IS_FAR -> "A"
        UNKNOWN -> null
    }

    companion object {
        fun fromKey(key: String?): ChannelMap = entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

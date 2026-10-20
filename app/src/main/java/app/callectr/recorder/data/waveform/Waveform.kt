/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.data.waveform

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The shape of a recording, reduced to something drawable.
 *
 * Drawn from the audio itself rather than animated: a bar that moves whether or not it matches what is
 * playing tells the user nothing, and implies a measurement that was never taken.
 *
 * A ninety-minute call is roughly 86 million samples against a few hundred pixels of screen, so all
 * that matters is which sample survives each bucket. It is the **loudest**, because averaging speech
 * against its own pauses flattens a conversation into one grey smear — and the reason to look at a
 * waveform at all is to see where the talking is.
 */
object Waveform {

    /** How many bars a waveform is stored as. Enough to show structure, small enough to cache as text. */
    const val BUCKETS = 240

    /**
     * Builds the same [BUCKETS] peaks as [reduce], but from audio handed over in pieces, so the whole
     * recording never has to be resident at once.
     *
     * This is what lets a long call be drawn at all. [reduce] needs every sample of the call in one
     * `FloatArray`; a decode big enough to fill it runs the heap out of memory on a long recording,
     * which left the waveform empty — and, because the waveform *is* the scrubber, left nothing to seek
     * with either (issue #52). Decoding in bounded time-windows and folding each into this accumulator
     * keeps peak memory independent of call length.
     *
     * Samples are placed into bars by **where they are in time**, not by index into one array, so
     * windows decoded separately — and even overlapping, as a seek-to-previous-sync run-up produces —
     * land in the right bars no matter where the cuts fell. Bar `b` covers
     * `[b/buckets, (b+1)/buckets)` of [totalMs].
     */
    class PeakAccumulator(private val buckets: Int, private val totalMs: Long) {
        private val out = FloatArray(buckets.coerceAtLeast(0))

        /**
         * Folds the first [length] mono samples of [samples], which begin at [startMs] in the call, into
         * the bars they fall in. Loudest-wins, exactly as [reduce] does.
         *
         * [sampleRateHz] is the rate of [samples] — the decoded rate the waveform is computed at, not the
         * file's rate. A sample landing past the declared end (a container that under-declares its
         * length) clamps into the last bar rather than being dropped.
         */
        fun add(samples: FloatArray, length: Int, startMs: Long, sampleRateHz: Int) {
            if (buckets <= 0 || totalMs <= 0L || sampleRateHz <= 0) return
            val msPerSample = 1000.0 / sampleRateHz
            val scale = buckets.toDouble() / totalMs
            for (i in 0 until length) {
                val atMs = startMs + i * msPerSample
                val bucket = (atMs * scale).toInt().coerceIn(0, buckets - 1)
                val magnitude = abs(samples[i])
                if (magnitude > out[bucket]) out[bucket] = magnitude
            }
        }

        /** The bars accumulated so far, each clamped to 0..1. Safe to read more than once. */
        fun peaks(): FloatArray = FloatArray(out.size) { out[it].coerceIn(0f, 1f) }
    }

    /** Peaks in 0..1, one per bucket, always exactly [buckets] long. */
    fun reduce(samples: FloatArray, buckets: Int): FloatArray {
        if (buckets <= 0) return FloatArray(0)
        val out = FloatArray(buckets)
        if (samples.isEmpty()) return out

        // Fractional bucket width, so a recording shorter than the bar still spreads across it rather
        // than crowding into the first few bars.
        val width = samples.size.toDouble() / buckets
        for (i in 0 until buckets) {
            val from = (i * width).toInt().coerceIn(0, samples.lastIndex)
            val to = ((i + 1) * width).toInt().coerceIn(from + 1, samples.size)
            var peak = 0f
            for (s in from until to) {
                val magnitude = abs(samples[s])
                if (magnitude > peak) peak = magnitude
            }
            out[i] = peak.coerceIn(0f, 1f)
        }
        return out
    }

    /**
     * Peaks as text for storage.
     *
     * Two digits each: the drawing is a few hundred pixels tall at most, so more precision would be
     * stored and never seen.
     */
    fun encode(peaks: FloatArray): String =
        peaks.joinToString(",") { (it * 99).roundToInt().coerceIn(0, 99).toString() }

    /** Peaks from [encoded]; empty when it is absent or unreadable, which callers treat as "not yet". */
    fun decode(encoded: String): FloatArray {
        if (encoded.isBlank()) return FloatArray(0)
        val parts = encoded.split(',')
        val out = FloatArray(parts.size)
        parts.forEachIndexed { i, part ->
            val value = part.trim().toIntOrNull() ?: return FloatArray(0)
            out[i] = (value / 99f).coerceIn(0f, 1f)
        }
        return out
    }
}

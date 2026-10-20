/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.data.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning a call into the handful of numbers a waveform is drawn from.
 *
 * A ninety-minute call is about 86 million samples and the bar is a few hundred pixels wide, so the
 * only question that matters is which sample survives each bucket — and it has to be the loudest,
 * because averaging speech against its own silences flattens the whole thing to a smear.
 */
class WaveformTest {

    @Test
    fun each_bucket_keeps_its_loudest_moment() {
        // Averaging would report this as quiet; the point of the drawing is to show where the talking
        // is, and a single loud sample in a quiet stretch is exactly that.
        val samples = floatArrayOf(0f, 0f, 0.9f, 0f, 0f, 0f, 0.2f, 0f)

        val peaks = Waveform.reduce(samples, buckets = 2)

        assertEquals(2, peaks.size)
        assertEquals(0.9f, peaks[0], 0.001f)
        assertEquals(0.2f, peaks[1], 0.001f)
    }

    @Test
    fun a_negative_swing_counts_as_loud_as_a_positive_one() {
        // Audio is signed and roughly symmetric around zero. Taking the raw maximum rather than the
        // magnitude would draw silence for anything that happened to dip.
        val peaks = Waveform.reduce(floatArrayOf(-0.8f, -0.1f), buckets = 1)

        assertEquals(0.8f, peaks[0], 0.001f)
    }

    @Test
    fun the_result_is_always_the_width_that_was_asked_for() {
        // The drawing code indexes straight into this; a short array would be a crash on a short call.
        assertEquals(64, Waveform.reduce(FloatArray(1_000) { 0.5f }, buckets = 64).size)
        assertEquals(64, Waveform.reduce(FloatArray(3) { 0.5f }, buckets = 64).size)
    }

    @Test
    fun a_recording_with_no_audio_produces_a_flat_line_not_a_crash() {
        val peaks = Waveform.reduce(FloatArray(0), buckets = 8)

        assertEquals(8, peaks.size)
        assertTrue(peaks.all { it == 0f })
    }

    @Test
    fun peaks_stay_inside_zero_to_one() {
        // Decoding can overshoot slightly; a bar taller than the canvas would be clipped by the drawing
        // and would silently rescale everything else.
        val peaks = Waveform.reduce(floatArrayOf(1.4f, -2f), buckets = 1)

        assertTrue(peaks.all { it in 0f..1f })
    }

    @Test
    fun peaks_survive_a_round_trip_through_storage() {
        // Cached so a ninety-minute call is decoded once rather than on every visit.
        val peaks = floatArrayOf(0f, 0.25f, 1f, 0.5f)

        val restored = Waveform.decode(Waveform.encode(peaks))

        assertEquals(peaks.size, restored.size)
        peaks.forEachIndexed { i, v -> assertEquals(v, restored[i], 0.01f) }
    }

    @Test
    fun a_corrupt_cache_reads_as_no_waveform_rather_than_a_crash() {
        assertTrue(Waveform.decode("").isEmpty())
        assertTrue(Waveform.decode("not,a,waveform").isEmpty())
    }

    // --- Streaming accumulator (issue #52): the same bars, built from pieces so a long call never has
    // to be decoded whole and run the heap out of memory. ---

    @Test
    fun streaming_a_whole_call_in_pieces_matches_decoding_it_at_once() {
        // 1 kHz so one sample is exactly one millisecond: eight samples span eight ms. The shape is the
        // same spike-in-each-half the first test uses, so the loudest-per-bar answer is known.
        val samples = floatArrayOf(0f, 0f, 0.9f, 0f, 0f, 0f, 0.2f, 0f)
        val expected = Waveform.reduce(samples, buckets = 2)

        val accumulator = Waveform.PeakAccumulator(buckets = 2, totalMs = 8)
        // Two separate decodes, each placed by where it starts in the call — never both resident at once.
        accumulator.add(samples.copyOfRange(0, 4), length = 4, startMs = 0, sampleRateHz = 1000)
        accumulator.add(samples.copyOfRange(4, 8), length = 4, startMs = 4, sampleRateHz = 1000)

        val peaks = accumulator.peaks()
        assertEquals(expected.size, peaks.size)
        expected.forEachIndexed { i, v -> assertEquals(v, peaks[i], 0.001f) }
    }

    @Test
    fun a_sample_lands_in_the_bar_for_its_place_in_time() {
        // A single loud sample three quarters of the way through a four-bar, 100 ms call must fall in the
        // last bar — placement is by time, not by arrival order.
        val accumulator = Waveform.PeakAccumulator(buckets = 4, totalMs = 100)
        accumulator.add(floatArrayOf(0.7f), length = 1, startMs = 75, sampleRateHz = 1000)

        val peaks = accumulator.peaks()
        assertEquals(0f, peaks[0], 0.001f)
        assertEquals(0.7f, peaks[3], 0.001f)
    }

    @Test
    fun a_window_redecoded_as_run_up_does_not_corrupt_its_bar() {
        // Seeking to the previous sync point can start a window slightly before it was asked for, so the
        // tail of one window and the head of the next overlap. Loudest-wins makes that harmless: feeding
        // the same stretch twice must leave the bar exactly where one pass would.
        val once = Waveform.PeakAccumulator(buckets = 2, totalMs = 10)
        once.add(floatArrayOf(0.5f), length = 1, startMs = 2, sampleRateHz = 1000)

        val twice = Waveform.PeakAccumulator(buckets = 2, totalMs = 10)
        twice.add(floatArrayOf(0.5f), length = 1, startMs = 2, sampleRateHz = 1000)
        twice.add(floatArrayOf(0.5f), length = 1, startMs = 2, sampleRateHz = 1000)

        assertEquals(once.peaks()[0], twice.peaks()[0], 0.001f)
    }

    @Test
    fun a_sample_past_the_declared_end_clamps_into_the_last_bar() {
        // A container can under-declare its length; the extra audio belongs at the end, not dropped.
        val accumulator = Waveform.PeakAccumulator(buckets = 2, totalMs = 10)
        accumulator.add(floatArrayOf(0.6f), length = 1, startMs = 999, sampleRateHz = 1000)

        assertEquals(0.6f, accumulator.peaks()[1], 0.001f)
    }

    @Test
    fun the_accumulator_is_always_the_width_that_was_asked_for() {
        // The drawing indexes straight into this, exactly as it does into reduce().
        val accumulator = Waveform.PeakAccumulator(buckets = 64, totalMs = 1000)
        accumulator.add(FloatArray(500) { 0.5f }, length = 500, startMs = 0, sampleRateHz = 1000)

        assertEquals(64, accumulator.peaks().size)
    }

    @Test
    fun an_unknown_length_accumulator_stays_flat_rather_than_crashing() {
        // totalMs 0 is what a container declaring no duration reports; there is no timeline to place
        // samples on, so nothing is folded in and the bars stay flat — the caller falls back elsewhere.
        val accumulator = Waveform.PeakAccumulator(buckets = 4, totalMs = 0)
        accumulator.add(floatArrayOf(0.9f), length = 1, startMs = 0, sampleRateHz = 1000)

        assertTrue(accumulator.peaks().all { it == 0f })
    }

    @Test
    fun accumulated_peaks_stay_inside_zero_to_one() {
        val accumulator = Waveform.PeakAccumulator(buckets = 1, totalMs = 10)
        accumulator.add(floatArrayOf(1.4f, -2f), length = 2, startMs = 0, sampleRateHz = 1000)

        assertTrue(accumulator.peaks().all { it in 0f..1f })
    }
}

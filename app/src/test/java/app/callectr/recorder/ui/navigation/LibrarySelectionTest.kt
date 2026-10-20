/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A multi-selection that cannot cross between two library pages.
 *
 * Worth pinning because the failure is silent and the next tap is a delete: Transcripts and
 * Summaries are two lists of display names with a large overlap, so a set carried from one to the
 * other arrives looking like a perfectly valid selection of different things.
 */
class LibrarySelectionTest {

    @Test
    fun nothing_is_selected_to_begin_with() {
        assertTrue(LibrarySelection.EMPTY.names.isEmpty())
        assertNull(LibrarySelection.EMPTY.section)
        assertTrue(LibrarySelection.EMPTY.on(HomeSection.Transcripts).isEmpty())
    }

    @Test
    fun selection_mode_is_closed_to_begin_with() {
        assertFalse(LibrarySelection.EMPTY.active)
        assertFalse(LibrarySelection.EMPTY.activeOn(HomeSection.Transcripts))
    }

    @Test
    fun a_row_picked_on_transcripts_is_selected_there() {
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Transcripts, "a.ogg")
        assertEquals(setOf("a.ogg"), selection.on(HomeSection.Transcripts))
    }

    @Test
    fun a_row_picked_on_transcripts_is_not_selected_on_summaries() {
        // The leak this type exists to stop. The same call is usually in both lists, so the names
        // would resolve and the bulk delete would run — against the wrong page's meaning of Delete.
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Transcripts, "a.ogg")
        assertTrue(selection.on(HomeSection.Summaries).isEmpty())
    }

    @Test
    fun picking_on_another_page_replaces_the_selection_rather_than_adding_to_it() {
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Transcripts, "a.ogg")
            .toggled(HomeSection.Transcripts, "b.ogg")
            .toggled(HomeSection.Summaries, "c.ogg")

        assertEquals(setOf("c.ogg"), selection.on(HomeSection.Summaries))
        assertTrue(selection.on(HomeSection.Transcripts).isEmpty())
    }

    @Test
    fun tapping_a_selected_row_again_takes_it_out() {
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Transcripts, "a.ogg")
            .toggled(HomeSection.Transcripts, "b.ogg")
            .toggled(HomeSection.Transcripts, "a.ogg")

        assertEquals(setOf("b.ogg"), selection.on(HomeSection.Transcripts))
    }

    @Test
    fun deselecting_the_last_row_keeps_selection_mode_open() {
        // The maintainer's rule (2026-10-12): clearing a selection — by un-picking the last row, or
        // by the "Deselect all" row — must NOT drop out of selection mode. The bar stays up at zero
        // picked; only the back arrow leaves (which sets EMPTY). So the section and the active flag
        // survive an empty name set.
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Transcripts, "a.ogg")
            .toggled(HomeSection.Transcripts, "a.ogg")

        assertTrue(selection.names.isEmpty())
        assertTrue(selection.active)
        assertEquals(HomeSection.Transcripts, selection.section)
        assertTrue(selection.activeOn(HomeSection.Transcripts))
        // Still gated by page: the other library is not in selection mode.
        assertFalse(selection.activeOn(HomeSection.Summaries))
    }

    @Test
    fun select_all_then_deselect_all_stays_in_mode() {
        val all = LibrarySelection.EMPTY.selectedAll(HomeSection.Summaries, setOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), all.on(HomeSection.Summaries))
        assertTrue(all.activeOn(HomeSection.Summaries))

        // Empty names is the "Deselect all" action: cleared, but the mode stays open on the page.
        val cleared = all.selectedAll(HomeSection.Summaries, emptySet())
        assertTrue(cleared.names.isEmpty())
        assertTrue(cleared.active)
        assertTrue(cleared.activeOn(HomeSection.Summaries))
    }

    @Test
    fun backing_out_is_the_only_thing_that_closes_selection_mode() {
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Transcripts, "a.ogg")
        assertTrue(selection.active)

        // The back arrow / clearSelection sets EMPTY — the one representation of "mode closed".
        assertFalse(LibrarySelection.EMPTY.active)
    }

    @Test
    fun a_name_that_is_in_both_libraries_is_still_only_selected_where_it_was_picked() {
        // The concrete shape of the overlap: most summarised calls are transcribed ones.
        val shared = "20260916_120000.000+0300_Dad.ogg"
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Summaries, shared)

        assertTrue(selection.on(HomeSection.Summaries).contains(shared))
        assertFalse(selection.on(HomeSection.Transcripts).contains(shared))
        assertFalse(selection.on(HomeSection.Recordings).contains(shared))
    }
}

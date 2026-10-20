package app.callectr.recorder.system.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreHelperTest {

    @Test
    fun `android 12 and newer use Recordings Callectr`() {
        assertEquals("Recordings/Callectr", MediaStoreHelper.relativePathForSdk(31))
        assertEquals("Recordings/Callectr", MediaStoreHelper.relativePathForSdk(36))
    }

    @Test
    fun `android 11 falls back to Music Callectr`() {
        assertEquals("Music/Callectr", MediaStoreHelper.relativePathForSdk(30))
    }
}

package dev.statup.app.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the pure retry/wipe control-flow for a corrupt EncryptedSharedPreferences file: retry
 * once without wiping (transient Keystore flakes), then wipe once if it's still undecryptable.
 */
class SecretStorageRecoveryTest {

    @Test
    fun `does not wipe when the first open succeeds`() {
        var attempts = 0
        var wiped = false

        val result = openWithRecovery(
            open = { attempts++; "opened" },
            onCorrupt = { wiped = true }
        )

        assertEquals("opened", result)
        assertEquals(1, attempts)
        assertFalse("must not wipe a healthy store", wiped)
    }

    @Test
    fun `transient failure recovers via plain retry without wiping`() {
        var attempts = 0
        var wiped = false

        val result = openWithRecovery(
            open = {
                attempts++
                if (attempts == 1) throw IllegalStateException("transient Keystore flake")
                "opened"
            },
            onCorrupt = { wiped = true }
        )

        assertEquals("opened", result)
        assertEquals(2, attempts)
        assertFalse("a transient failure must NOT wipe the user's stored secrets", wiped)
    }

    @Test
    fun `persistent failure wipes once and reopens`() {
        var attempts = 0
        var wiped = false

        val result = openWithRecovery(
            open = {
                attempts++
                if (attempts <= 2) throw IllegalStateException("undecryptable after restore")
                "opened"
            },
            onCorrupt = { wiped = true }
        )

        assertEquals("opened", result)
        assertEquals(3, attempts)
        assertTrue("should have wiped the corrupt file before the final open", wiped)
    }

    @Test
    fun `propagates when even the post-wipe open fails`() {
        var wiped = false

        try {
            openWithRecovery<String>(
                open = { throw IllegalStateException("hard failure") },
                onCorrupt = { wiped = true }
            )
            fail("expected the final failure to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("hard failure", e.message)
        }
        assertTrue("the wipe should still have been attempted", wiped)
    }
}

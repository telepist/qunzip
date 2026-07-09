@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package qunzip.integration

import qunzip.platform.RegistryHelper
import qunzip.platform.HKEY_CURRENT_USER
import kotlin.test.*

/**
 * Integration tests for the registry helper against a throwaway per-user key
 * (never touches real file associations).
 */
class RegistryIntegrationTest {

    private val helper = RegistryHelper()
    private val base = "Software\\QuickUnzipTest"

    @AfterTest
    fun cleanup() {
        helper.deleteKeyTree(HKEY_CURRENT_USER, base)
    }

    @Test
    fun `deleteKeyTree removes a key with nested subkeys`() {
        // Build base\a\b plus a sibling subkey (mirrors an extension key that has
        // an OpenWithProgids subkey — the case plain RegDeleteKeyW can't delete).
        helper.createKey(HKEY_CURRENT_USER, "$base\\a\\b")?.let { helper.closeKey(it) }
        helper.createKey(HKEY_CURRENT_USER, "$base\\OpenWithProgids")?.let { helper.closeKey(it) }
        assertNotNull(
            helper.openKey(HKEY_CURRENT_USER, base),
            "precondition: base key should exist"
        )

        val deleted = helper.deleteKeyTree(HKEY_CURRENT_USER, base)

        assertTrue(deleted, "deleteKeyTree should report success")
        assertNull(
            helper.openKey(HKEY_CURRENT_USER, base),
            "base key (and its subkeys) should be gone"
        )
    }
}

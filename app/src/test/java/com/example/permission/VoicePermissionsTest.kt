package com.example.permission

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoicePermissionsTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRequiredPermissionConstants() {
        assertEquals(Manifest.permission.READ_CONTACTS, VoicePermissions.PERMISSION_READ_CONTACTS)
        assertEquals(Manifest.permission.CALL_PHONE, VoicePermissions.PERMISSION_CALL_PHONE)
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, VoicePermissions.PERMISSION_READ_MEDIA_AUDIO)
        assertEquals("android.permission.QUERY_ALL_PACKAGES", VoicePermissions.PERMISSION_QUERY_ALL_PACKAGES)
        assertEquals(Manifest.permission.RECORD_AUDIO, VoicePermissions.PERMISSION_RECORD_AUDIO)
    }

    @Test
    fun testRequiredPermissionsArrayIncludesContactsMediaAndMic() {
        val permissions = VoicePermissions.getRequiredRuntimePermissions()
        assertTrue(permissions.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue(permissions.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(permissions.contains(Manifest.permission.CALL_PHONE))
        assertTrue(permissions.contains(VoicePermissions.PERMISSION_READ_MEDIA_AUDIO))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun testPermissionGroupStatuses() {
        val statuses = VoicePermissions.getPermissionGroupStatuses(context)
        assertEquals(4, statuses.size)

        val groups = statuses.map { it.group }
        assertTrue(groups.contains(VoicePermissions.PermissionGroup.MICROPHONE))
        assertTrue(groups.contains(VoicePermissions.PermissionGroup.CONTACTS))
        assertTrue(groups.contains(VoicePermissions.PermissionGroup.MEDIA))
        assertTrue(groups.contains(VoicePermissions.PermissionGroup.QUERY_PACKAGES))

        // Ensure Myanmar titles and descriptions are provided
        statuses.forEach { item ->
            assertFalse(item.group.titleMM.isBlank())
            assertFalse(item.group.descriptionMM.isBlank())
        }
    }

    @Test
    fun testGetMissingPermissions() {
        val missing = VoicePermissions.getMissingPermissions(context)
        assertNotNull(missing)
        // In clean ungranted test context, microphone and contacts are in missing list
        assertTrue(missing.contains(Manifest.permission.RECORD_AUDIO))
    }
}

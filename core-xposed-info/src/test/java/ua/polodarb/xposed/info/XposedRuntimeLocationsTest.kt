package ua.polodarb.xposed.info

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XposedRuntimeLocationsTest {

    @Test
    fun `device protected storage comes first for a credential protected data dir`() {
        assertEquals(
            listOf("/data/user_de/0/com.example", "/data/user/0/com.example"),
            XposedRuntimeLocations.dataDirectories("/data/user/0/com.example"),
        )
    }

    @Test
    fun `secondary users keep their user id`() {
        assertEquals(
            listOf("/data/user_de/10/com.example", "/data/user/10/com.example"),
            XposedRuntimeLocations.dataDirectories("/data/user/10/com.example"),
        )
    }

    @Test
    fun `legacy data dir resolves to the system user directories`() {
        assertEquals(
            listOf("/data/user_de/0/com.example", "/data/user/0/com.example"),
            XposedRuntimeLocations.dataDirectories("/data/data/com.example"),
        )
    }

    @Test
    fun `a device protected data dir resolves both directories`() {
        assertEquals(
            listOf("/data/user_de/0/com.example", "/data/user/0/com.example"),
            XposedRuntimeLocations.dataDirectories("/data/user_de/0/com.example"),
        )
    }

    @Test
    fun `trailing separators do not duplicate directories`() {
        assertEquals(
            listOf("/data/user_de/0/com.example", "/data/user/0/com.example"),
            XposedRuntimeLocations.dataDirectories("/data/user/0/com.example/"),
        )
    }

    @Test
    fun `an unrecognised data dir is used as is`() {
        assertEquals(
            listOf("/mnt/custom/com.example"),
            XposedRuntimeLocations.dataDirectories("/mnt/custom/com.example"),
        )
        assertNull(XposedRuntimeLocations.deviceProtectedDataDirectory("/mnt/custom/com.example"))
    }

    @Test
    fun `a blank data dir resolves to nothing`() {
        assertEquals(emptyList<String>(), XposedRuntimeLocations.dataDirectories(""))
    }

    @Test
    fun `runtime files are resolved inside every runtime directory`() {
        assertEquals(
            listOf(
                File("/data/user_de/0/com.example/gmsflags_xposed/runtime_overrides.db"),
                File("/data/user/0/com.example/gmsflags_xposed/runtime_overrides.db"),
            ),
            XposedRuntimeLocations.runtimeFiles(
                "/data/user/0/com.example",
                XposedConstants.RUNTIME_OVERRIDES_DB_FILE_NAME,
            ),
        )
    }
}

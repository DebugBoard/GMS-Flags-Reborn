package ua.polodarb.gmsflags.data.phenotype.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeStateMigratorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var primary: File
    private lateinit var legacy: File
    private val fileAccess = FakeFileAccess()
    private lateinit var migrator: RuntimeStateMigrator

    @Before
    fun setUp() {
        primary = File(temporaryFolder.root, "device-protected/gmsflags_xposed")
        legacy = temporaryFolder.newFolder("credential-protected", "gmsflags_xposed")
        migrator = RuntimeStateMigrator(
            directoryLocator = { listOf(primary, legacy) },
            fileAccess = fileAccess,
        )
    }

    @Test
    fun `overrides written by an earlier version are copied to the primary directory`() {
        File(legacy, "runtime_overrides.db").writeText("overrides")
        File(legacy, "overrides_paused").writeText("paused")

        migrator.migrate("android")

        assertEquals("overrides", File(primary, "runtime_overrides.db").readText())
        assertEquals("paused", File(primary, "overrides_paused").readText())
        assertEquals(2, fileAccess.preparedFiles.size)
        assertEquals(
            listOf("overrides_paused", "runtime_overrides.db"),
            primary.list().orEmpty().sorted(),
        )
    }

    @Test
    fun `the copy left behind keeps serving targets that are still running`() {
        File(legacy, "runtime_overrides.db").writeText("overrides")

        migrator.migrate("android")

        assertTrue(File(legacy, "runtime_overrides.db").isFile)
    }

    @Test
    fun `existing primary state is never overwritten`() {
        primary.mkdirs()
        File(primary, "runtime_overrides.db").writeText("current")
        File(legacy, "runtime_overrides.db").writeText("stale")

        migrator.migrate("android")

        assertEquals("current", File(primary, "runtime_overrides.db").readText())
        assertTrue(fileAccess.preparedFiles.isEmpty())
    }

    @Test
    fun `unrelated files are left alone`() {
        File(legacy, "hook_diagnostics.db").writeText("diagnostics")

        migrator.migrate("android")

        assertFalse(File(primary, "hook_diagnostics.db").exists())
    }

    @Test
    fun `a target is migrated only once`() {
        File(legacy, "runtime_overrides.db").writeText("overrides")

        migrator.migrate("android")
        File(primary, "runtime_overrides.db").delete()
        migrator.migrate("android")

        assertFalse(File(primary, "runtime_overrides.db").exists())
    }

    @Test
    fun `a copy the target could not read is removed`() {
        File(legacy, "runtime_overrides.db").writeText("overrides")
        fileAccess.failing = true

        migrator.migrate("android")

        assertFalse(File(primary, "runtime_overrides.db").exists())
        assertTrue(primary.list().orEmpty().isEmpty())
    }

    private class FakeFileAccess : RuntimeOverrideFileAccess {
        var failing = false
        val preparedFiles = mutableListOf<File>()

        override fun prepare(
            androidPackageName: String,
            databaseFile: File,
            restoreContext: Boolean,
        ) {
            if (failing) error("Unable to restore SELinux context")
            preparedFiles += databaseFile
        }
    }
}

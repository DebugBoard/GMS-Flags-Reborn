package ua.polodarb.gmsflags.data.phenotype.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeFlagOverrideStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val primaryFile = File("device-protected/runtime-overrides.db")
    private val legacyFile = File("credential-protected/runtime-overrides.db")
    private val database = FakeDatabase()
    private val access = FakeFileAccess()
    private val store = RuntimeFlagOverrideStore(
        database = database,
        locator = RuntimeOverrideDatabaseLocator { listOf(primaryFile, legacyFile) },
        fileAccess = access,
    )

    @Test
    fun `overrides are written to the primary database only`() {
        store.write("android", "phenotype", listOf(RuntimeFlagOverride("phenotype", "flag", 0, "1")))

        assertEquals(listOf(primaryFile), database.writtenFiles)
    }

    @Test
    fun `restores file context only on first write for target`() {
        val override = RuntimeFlagOverride("phenotype", "flag", 0, "1")

        store.write("android", "phenotype", listOf(override))
        store.write("android", "phenotype", listOf(override))

        assertEquals(listOf(true, false), access.restoreContextCalls)
    }

    @Test
    fun `missing database delete does not prepare file permissions`() {
        database.deleteResult = false

        store.delete("android", "phenotype", "flag")

        assertTrue(access.restoreContextCalls.isEmpty())
    }

    @Test
    fun `delete all clears every runtime database and preserves file ownership`() {
        store.deleteAll("android")

        assertEquals(listOf(primaryFile, legacyFile), database.deleteAllFiles)
        assertEquals(listOf(false, false), access.restoreContextCalls)
    }

    @Test
    fun `deleting an override reaches the database written by an earlier version`() {
        store.delete("android", "phenotype", "flag")

        assertEquals(listOf(primaryFile, legacyFile), database.deletedFiles)
    }

    @Test
    fun `reads fall back to the database written by an earlier version`() {
        val legacy = temporaryFolder.newFile("legacy-runtime-overrides.db")
        val missingPrimary = File(temporaryFolder.root, "runtime-overrides.db")
        val fallbackStore = RuntimeFlagOverrideStore(
            database = database,
            locator = RuntimeOverrideDatabaseLocator { listOf(missingPrimary, legacy) },
            fileAccess = access,
        )

        fallbackStore.read("android", "phenotype")

        assertEquals(listOf(legacy), database.readFiles)
    }

    private class FakeDatabase : RuntimeOverrideDatabase {
        var deleteResult = true
        val readFiles = mutableListOf<File>()
        val writtenFiles = mutableListOf<File>()
        val deletedFiles = mutableListOf<File>()
        val deleteAllFiles = mutableListOf<File>()

        override fun read(file: File, phenotypePackageName: String): List<RuntimeFlagOverride> {
            readFiles += file
            return emptyList()
        }

        override fun write(
            file: File,
            phenotypePackageName: String,
            overrides: List<RuntimeFlagOverride>,
        ) {
            writtenFiles += file
        }

        override fun writeMicroHooks(
            file: File,
            androidPackageName: String,
            hooks: List<RuntimeMicroHookOverride>,
        ) {
            writtenFiles += file
        }

        override fun delete(file: File, phenotypePackageName: String, flagName: String): Boolean {
            deletedFiles += file
            return deleteResult
        }

        override fun delete(
            file: File,
            phenotypePackageName: String,
            flagNames: List<String>,
        ): Boolean {
            deletedFiles += file
            return deleteResult
        }

        override fun deletePackage(file: File, phenotypePackageName: String): Boolean {
            deletedFiles += file
            return deleteResult
        }

        override fun deleteMicroHooks(
            file: File,
            androidPackageName: String,
            recipeIds: List<Long>,
        ): Boolean {
            deletedFiles += file
            return deleteResult
        }

        override fun deleteAll(file: File): Boolean {
            deleteAllFiles += file
            return deleteResult
        }
    }

    private class FakeFileAccess : RuntimeOverrideFileAccess {
        val restoreContextCalls = mutableListOf<Boolean>()

        override fun prepare(
            androidPackageName: String,
            databaseFile: File,
            restoreContext: Boolean,
        ) {
            restoreContextCalls += restoreContext
        }
    }
}

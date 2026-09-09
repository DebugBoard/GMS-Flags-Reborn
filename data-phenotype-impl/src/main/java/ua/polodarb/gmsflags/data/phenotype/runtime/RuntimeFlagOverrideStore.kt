package ua.polodarb.gmsflags.data.phenotype.runtime

import android.content.Context
import java.io.File

internal data class RuntimeFlagOverride(
    val packageName: String,
    val name: String,
    val type: Int,
    val value: String,
)

internal data class RuntimeMicroHookOverride(
    val recipeId: Long,
    val payloadBase64: String,
    val payloadSha256: String,
    val signatureBase64: String,
    val required: Boolean,
)

/**
 * Reads and writes the runtime state the Xposed module picks up inside every target app.
 *
 * Overrides are written to the primary location only - device-protected storage, so a target that
 * starts before the first unlock after a reboot can read them - while reads fall back to, and
 * deletes cover, the locations used by earlier versions.
 */
internal class RuntimeFlagOverrideStore(
    private val database: RuntimeOverrideDatabase,
    private val locator: RuntimeOverrideDatabaseLocator,
    private val fileAccess: RuntimeOverrideFileAccess,
) {
    private val preparedTargets = mutableSetOf<String>()

    constructor(context: Context) : this(
        database = SqliteRuntimeOverrideDatabase(),
        locator = AndroidRuntimeOverrideDatabaseLocator(context.packageManager),
        fileAccess = AndroidRuntimeOverrideFileAccess(context.packageManager),
    )

    fun read(
        androidPackageName: String,
        phenotypePackageName: String,
    ): List<RuntimeFlagOverride> {
        val file = readableFile(androidPackageName) ?: return emptyList()
        return database.read(file = file, phenotypePackageName = phenotypePackageName)
    }

    fun write(
        androidPackageName: String,
        phenotypePackageName: String,
        overrides: List<RuntimeFlagOverride>,
    ) {
        if (overrides.isEmpty()) return
        val file = primaryFile(androidPackageName) ?: return
        database.write(file, phenotypePackageName, overrides)
        fileAccess.prepare(
            androidPackageName = androidPackageName,
            databaseFile = file,
            restoreContext = preparedTargets.add(androidPackageName),
        )
    }

    fun writeMicroHooks(
        androidPackageName: String,
        hooks: List<RuntimeMicroHookOverride>,
    ) {
        if (hooks.isEmpty()) return
        val file = primaryFile(androidPackageName) ?: return
        database.writeMicroHooks(file, androidPackageName, hooks)
        fileAccess.prepare(
            androidPackageName = androidPackageName,
            databaseFile = file,
            restoreContext = preparedTargets.add(androidPackageName),
        )
    }

    fun delete(
        androidPackageName: String,
        phenotypePackageName: String,
        flagName: String,
    ) = editEveryFile(androidPackageName) { file ->
        database.delete(file, phenotypePackageName, flagName)
    }

    fun delete(
        androidPackageName: String,
        phenotypePackageName: String,
        flagNames: List<String>,
    ) {
        if (flagNames.isEmpty()) return
        editEveryFile(androidPackageName) { file ->
            database.delete(file, phenotypePackageName, flagNames)
        }
    }

    fun deletePackage(
        androidPackageName: String,
        phenotypePackageName: String,
    ) = editEveryFile(androidPackageName) { file ->
        database.deletePackage(file, phenotypePackageName)
    }

    fun deleteMicroHooks(
        androidPackageName: String,
        recipeIds: List<Long>,
    ) {
        if (recipeIds.isEmpty()) return
        editEveryFile(androidPackageName) { file ->
            database.deleteMicroHooks(file, androidPackageName, recipeIds)
        }
    }

    fun deleteAll(androidPackageName: String) = editEveryFile(androidPackageName) { file ->
        database.deleteAll(file)
    }

    private fun primaryFile(androidPackageName: String): File? =
        locate(androidPackageName).firstOrNull()

    private fun readableFile(androidPackageName: String): File? =
        locate(androidPackageName).firstOrNull(File::isFile) ?: primaryFile(androidPackageName)

    /**
     * Removals have to reach every location: a copy left behind by an earlier version would
     * otherwise resurrect the removed overrides once the primary database is cleared.
     */
    private fun editEveryFile(androidPackageName: String, edit: (File) -> Boolean) {
        locate(androidPackageName).forEach { file ->
            if (edit(file)) fileAccess.prepare(androidPackageName, file, restoreContext = false)
        }
    }

    private fun locate(androidPackageName: String): List<File> =
        runCatching { locator.locate(androidPackageName) }.getOrDefault(emptyList())
}

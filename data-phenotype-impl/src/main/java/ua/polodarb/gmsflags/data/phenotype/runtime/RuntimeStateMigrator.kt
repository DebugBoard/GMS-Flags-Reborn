package ua.polodarb.gmsflags.data.phenotype.runtime

import java.io.File
import ua.polodarb.xposed.info.XposedConstants

/**
 * Carries runtime state written by earlier versions over to device-protected storage.
 *
 * Overrides used to live only in `/data/user/<user>/<package>/gmsflags_xposed`, which a target app
 * cannot read until the device is unlocked for the first time after a reboot. Targets that start
 * before that - Gboard above all - therefore came up with no overrides at all and kept running
 * without them until the process was restarted by hand. The state now lives in device-protected
 * storage, and this copies over what existing installs already have.
 *
 * The legacy copies are left in place: targets that are still running the code loaded before the
 * app was updated only know about them, and every removal goes through both locations anyway.
 */
internal class RuntimeStateMigrator(
    private val directoryLocator: RuntimeDirectoryLocator,
    private val fileAccess: RuntimeOverrideFileAccess,
) {
    private val migrated = mutableSetOf<String>()

    fun migrate(androidPackageNames: Collection<String>) {
        androidPackageNames.distinct().forEach(::migrate)
    }

    @Synchronized
    fun migrate(androidPackageName: String) {
        if (!migrated.add(androidPackageName)) return

        val directories = directoryLocator.locate(androidPackageName)
        val primary = directories.firstOrNull() ?: return
        directories.drop(1).forEach { legacy -> copyState(androidPackageName, legacy, primary) }
    }

    private fun copyState(androidPackageName: String, from: File, to: File) {
        val files = runCatching {
            from.listFiles { file: File -> file.isFile && file.name.isRuntimeState() }
        }.getOrNull()?.toList().orEmpty()

        files.forEach { file ->
            val destination = File(to, file.name)
            if (destination.exists()) return@forEach
            // Staged under a temporary name and renamed into place, so a target starting while the
            // copy runs never reads a half-written database.
            val staged = File(to, "${file.name}$STAGED_SUFFIX")
            runCatching {
                to.mkdirs()
                staged.delete()
                file.copyTo(staged, overwrite = true)
                fileAccess.prepare(androidPackageName, staged, restoreContext = true)
                check(staged.renameTo(destination)) { "Unable to move ${staged.path} into place" }
            }.onFailure {
                // A copy the target cannot read would hide the legacy one, which still works.
                staged.delete()
                destination.delete()
            }
        }
    }

    private fun String.isRuntimeState(): Boolean =
        !endsWith(STAGED_SUFFIX) &&
            (
                startsWith(XposedConstants.RUNTIME_OVERRIDES_DB_FILE_NAME) ||
                    this == XposedConstants.OVERRIDES_PAUSED_FILE_NAME
                )

    private companion object {
        const val STAGED_SUFFIX = ".migrating"
    }
}

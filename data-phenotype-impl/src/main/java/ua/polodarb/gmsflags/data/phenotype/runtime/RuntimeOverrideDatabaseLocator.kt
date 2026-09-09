package ua.polodarb.gmsflags.data.phenotype.runtime

import android.content.pm.PackageManager
import java.io.File
import ua.polodarb.xposed.info.XposedConstants
import ua.polodarb.xposed.info.XposedRuntimeLocations

internal fun interface RuntimeDirectoryLocator {
    /**
     * Every runtime directory of the target, the primary one first. Runtime state is written to
     * the primary directory - the one in device-protected storage, which the hook can read even
     * when the target starts before the first unlock after a reboot - while the remaining
     * candidates are still read, because they may hold state written by earlier versions.
     */
    fun locate(androidPackageName: String): List<File>
}

internal class AndroidRuntimeDirectoryLocator(
    private val packageManager: PackageManager,
) : RuntimeDirectoryLocator {
    override fun locate(androidPackageName: String): List<File> = runCatching {
        XposedRuntimeLocations.runtimeDirectories(
            packageManager.getApplicationInfo(androidPackageName, 0).dataDir
        )
    }.getOrDefault(emptyList())
}

internal fun interface RuntimeOverrideDatabaseLocator {
    /** Every override database of the target, the primary one first. */
    fun locate(androidPackageName: String): List<File>
}

internal class AndroidRuntimeOverrideDatabaseLocator(
    private val directoryLocator: RuntimeDirectoryLocator,
) : RuntimeOverrideDatabaseLocator {
    constructor(packageManager: PackageManager) : this(
        AndroidRuntimeDirectoryLocator(packageManager),
    )

    override fun locate(androidPackageName: String): List<File> = directoryLocator
        .locate(androidPackageName)
        .map { directory -> File(directory, XposedConstants.RUNTIME_OVERRIDES_DB_FILE_NAME) }
}

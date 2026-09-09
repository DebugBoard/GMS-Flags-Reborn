package ua.polodarb.xposed.runtime

import android.content.Context
import java.io.File
import ua.polodarb.xposed.info.XposedConstants
import ua.polodarb.xposed.info.XposedRuntimeLocations

/**
 * Resolves the runtime directories of the hooked app.
 *
 * Reads have to consider every candidate: device-protected storage comes first, because a target
 * started before the first unlock after a reboot cannot read credential-protected storage at all,
 * and credential-protected storage is still read as a fallback for state written by earlier
 * versions of the app. Writes go to the first directory this process can actually write to.
 */
internal class XposedRuntimeDirectory {

    fun candidates(dataDirectory: String?): List<File> = dataDirectory
        ?.let(XposedRuntimeLocations::runtimeDirectories)
        .orEmpty()
        .distinctBy(File::getAbsolutePath)

    fun candidates(context: Context): List<File> {
        val dataDirectory = runCatching { context.dataDir }.getOrNull()
        return candidates(dataDirectory?.path)
            .ifEmpty { listOfNotNull(dataDirectory?.let { File(it, XposedConstants.XPOSED_DIR) }) }
    }

    fun writable(candidates: List<File>): File? = candidates.firstOrNull(::isWritable)

    fun resolve(context: Context): File {
        val candidates = candidates(context)
        return writable(candidates)
            ?: candidates.firstOrNull()
            ?: File(context.dataDir, XposedConstants.XPOSED_DIR)
    }

    private fun isWritable(directory: File): Boolean = runCatching {
        (directory.exists() || directory.mkdirs()) && directory.canWrite()
    }.getOrDefault(false)
}

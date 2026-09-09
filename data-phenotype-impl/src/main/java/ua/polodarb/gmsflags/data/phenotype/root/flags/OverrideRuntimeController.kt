package ua.polodarb.gmsflags.data.phenotype.root.flags

import android.content.pm.PackageManager
import android.system.Os
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File
import ua.polodarb.gmsflags.data.phenotype.runtime.RuntimeFlagOverrideStore
import ua.polodarb.xposed.info.XposedConstants
import ua.polodarb.xposed.info.XposedRuntimeLocations

internal class OverrideRuntimeController(
    private val packageManager: PackageManager,
    private val overrideStore: RuntimeFlagOverrideStore,
) {
    fun readOverrideCount(androidPackageNames: List<String>): Int =
        targets(androidPackageNames).sumOf { target ->
            val databaseFile = target
                .runtimeFiles(XposedConstants.RUNTIME_OVERRIDES_DB_FILE_NAME)
                .firstOrNull(File::isFile)
                ?: return@sumOf 0
            runCatching {
                SQLiteDatabase.openDatabase(
                    databaseFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { database ->
                    database.rawQuery(
                        "SELECT COUNT(*) FROM ${XposedConstants.RUNTIME_OVERRIDES_TABLE}",
                        null,
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                }
            }.getOrDefault(0)
        }

    fun readPaused(androidPackageNames: List<String>): Boolean {
        val targets = targets(androidPackageNames)
        return targets.isNotEmpty() && targets.all { target ->
            target.pauseMarkers().any(File::isFile)
        }
    }

    fun setPaused(androidPackageNames: List<String>, paused: Boolean) {
        targets(androidPackageNames).forEach { target ->
            if (paused) {
                val directory = target.runtimeDirectories.firstOrNull() ?: return@forEach
                val marker = File(directory, XposedConstants.OVERRIDES_PAUSED_FILE_NAME)
                directory.mkdirs()
                marker.writeText("paused")
                Os.chown(directory.path, target.uid, target.uid)
                Os.chmod(directory.path, MODE_OWNER_DIRECTORY)
                Os.chown(marker.path, target.uid, target.uid)
                Os.chmod(marker.path, MODE_OWNER_FILE)
                restoreContext(directory, marker)
            } else {
                // Every location, so a marker left by an earlier version cannot keep the hook off.
                target.pauseMarkers().forEach(File::delete)
            }
        }
    }

    fun deleteAll(androidPackageNames: List<String>) {
        targets(androidPackageNames).forEach { target ->
            overrideStore.deleteAll(target.packageName)
        }
    }

    private fun targets(androidPackageNames: List<String>): List<Target> = androidPackageNames
        .distinct()
        .mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                Target(
                    packageName = packageName,
                    uid = info.uid,
                    runtimeDirectories = XposedRuntimeLocations.runtimeDirectories(info.dataDir),
                )
            }.getOrNull()
        }

    private fun restoreContext(directory: File, marker: File) {
        val result = ProcessBuilder("restorecon", "-F", directory.path, marker.path)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        check(result == 0) { "Unable to restore SELinux context for override pause state" }
    }

    private data class Target(
        val packageName: String,
        val uid: Int,
        val runtimeDirectories: List<File>,
    ) {
        fun runtimeFiles(fileName: String): List<File> =
            runtimeDirectories.map { directory -> File(directory, fileName) }

        fun pauseMarkers(): List<File> =
            runtimeFiles(XposedConstants.OVERRIDES_PAUSED_FILE_NAME)
    }

    private companion object {
        const val MODE_OWNER_DIRECTORY = 448
        const val MODE_OWNER_FILE = 384
    }
}

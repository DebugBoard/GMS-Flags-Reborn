package ua.polodarb.xposed.store

import android.database.sqlite.SQLiteDatabase
import ua.polodarb.xposed.info.XposedConstants
import ua.polodarb.xposed.info.phenotypePackageCandidates
import java.io.File
import ua.polodarb.xposed.logging.XposedLogger

/**
 * Reads the overrides written by the app.
 *
 * [dbFiles] holds every location the database can live in, the primary one first: a target that
 * starts before the first unlock after a reboot only sees the copy in device-protected storage,
 * while state written by earlier versions of the app still lives in credential-protected storage.
 * The candidates are re-checked on every refresh, so the store also picks up the primary copy as
 * soon as it appears.
 */
internal class RuntimeFlagOverrideStore(
    private val dbFiles: List<File>
) {

    @Volatile
    private var snapshot = Snapshot(null, Long.MIN_VALUE, emptyMap())

    fun find(packageName: String, flagName: String): Override? =
        overrides()[Key(packageName, flagName)]

    fun hasOverrides(): Boolean = overrides().isNotEmpty()

    fun overrideCount(): Int = overrides().size

    fun findBestMatch(
        identityPackageName: String,
        contextPackageName: String,
        flagName: String,
    ): Match {
        val overrides = overrides()

        phenotypePackageCandidates(identityPackageName, contextPackageName).forEach { candidate ->
            overrides[Key(candidate.packageName, flagName)]?.let {
                return Match(it, candidate.source)
            }
        }

        return Match(null, "miss")
    }

    fun findBestMatches(
        identityPackageName: String,
        contextPackageName: String,
    ): Map<String, Match> {
        val overrides = overrides()
        val matches = linkedMapOf<String, Match>()

        phenotypePackageCandidates(identityPackageName, contextPackageName).forEach { candidate ->
            overrides.forEach { (key, override) ->
                if (key.packageName == candidate.packageName) {
                    matches.putIfAbsent(
                        key.flagName,
                        Match(override, candidate.source),
                    )
                }
            }
        }

        return matches
    }

    fun describeForLog(): String {
        val overrides = overrides()
        val packages = overrides.keys
            .groupingBy(Key::packageName)
            .eachCount()
            .entries
            .sortedByDescending(Map.Entry<String, Int>::value)
            .take(MAX_LOG_PACKAGES)
            .joinToString(prefix = "[", postfix = "]") { (packageName, count) ->
                "$packageName($count)"
            }
        val paths = dbFiles.joinToString(prefix = "[", postfix = "]") { file ->
            "${file.path}(exists=${file.isFile})"
        }
        return "paths=$paths, count=${overrides.size}, packages=$packages"
    }

    private fun overrides(): Map<Key, Override> {
        val dbFile = dbFiles.firstOrNull(File::isFile)
        val modifiedAt = dbFile?.lastModified() ?: Long.MIN_VALUE
        val current = snapshot
        if (dbFile?.path == current.path && modifiedAt == current.modifiedAt) {
            return current.overrides
        }

        val refreshed = Snapshot(
            path = dbFile?.path,
            modifiedAt = modifiedAt,
            overrides = dbFile?.let(::readOverrides).orEmpty(),
        )
        snapshot = refreshed
        return refreshed.overrides
    }

    private fun readOverrides(dbFile: File): Map<Key, Override> {
        val result = linkedMapOf<Key, Override>()
        val db = runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse {
            XposedLogger.logW("Runtime overrides DB is not readable: ${it.message}")
            return emptyMap()
        }

        db.use {
            runCatching {
                it.rawQuery(
                    """
                    SELECT packageName, name, flagType, value
                    FROM ${XposedConstants.RUNTIME_OVERRIDES_TABLE};
                    """.trimIndent(),
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val packageName = cursor.getString(0) ?: continue
                        val flagName = cursor.getString(1) ?: continue
                        result[Key(packageName, flagName)] = Override(
                            flagType = cursor.getInt(2),
                            value = cursor.getString(3) ?: ""
                        )
                    }
                }
            }.onFailure { error ->
                XposedLogger.logW("Failed to read runtime overrides: ${error.message}")
            }
        }

        return result
    }

    data class Override(
        val flagType: Int,
        val value: String,
    )

    data class Match(
        val override: Override?,
        val source: String,
    )

    private data class Key(
        val packageName: String,
        val flagName: String,
    )

    private data class Snapshot(
        val path: String?,
        val modifiedAt: Long,
        val overrides: Map<Key, Override>,
    )

    private companion object {
        const val MAX_LOG_PACKAGES = 8
    }
}

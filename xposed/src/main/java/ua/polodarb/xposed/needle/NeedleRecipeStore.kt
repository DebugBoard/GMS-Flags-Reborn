package ua.polodarb.xposed.needle

import android.database.sqlite.SQLiteDatabase
import ua.polodarb.xposed.info.XposedConstants
import ua.polodarb.xposed.info.needle.NeedleEnvelope
import ua.polodarb.xposed.info.needle.NeedleProtocol
import ua.polodarb.xposed.logging.XposedLogger
import java.io.File

/**
 * Reads the micro-hook recipes written by the app. [dbFiles] holds every location the database can
 * live in, the primary one first; see [ua.polodarb.xposed.info.XposedRuntimeLocations].
 */
internal class NeedleRecipeStore(private val dbFiles: List<File>) {

    fun findForPackage(packageName: String): List<NeedleEnvelope> {
        val dbFile = dbFiles.firstOrNull(File::isFile) ?: return emptyList()

        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT payloadBase64, payloadSha256, signatureBase64, required " +
                        "FROM ${XposedConstants.RUNTIME_MICRO_HOOKS_TABLE} WHERE packageName = ?",
                    arrayOf(packageName),
                ).use { cursor ->
                    val results = mutableListOf<NeedleEnvelope>()
                    while (cursor.moveToNext()) {
                        val payloadBase64 = cursor.getString(0) ?: continue
                        val payloadSha256 = cursor.getString(1) ?: continue
                        val signatureBase64 = cursor.getString(2) ?: continue
                        results += NeedleEnvelope(
                            mediaType = NeedleProtocol.RECIPE_MEDIA_TYPE,
                            payloadBase64 = payloadBase64,
                            payloadSha256 = payloadSha256,
                            signatureAlgorithm = NeedleProtocol.SIGNATURE_ALGORITHM,
                            signatureBase64 = signatureBase64,
                            required = cursor.getInt(3) != 0,
                        )
                    }
                    results
                }
            }
        }.onFailure { error ->
            XposedLogger.logW("NeedleRecipeStore: failed to read $packageName: ${error.message}")
        }.getOrDefault(emptyList())
    }
}

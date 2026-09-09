package ua.polodarb.xposed.info

import java.io.File

/**
 * Resolves the directories that keep the module's runtime state inside a target app.
 *
 * Credential-protected storage (`/data/user/<user>/<package>`) only becomes readable once the user
 * unlocks the device for the first time after a reboot. Direct-boot aware targets - Gboard above
 * all - are already running by then, so overrides kept there are invisible to the hook and stay
 * unapplied until the process is restarted by hand. Device-protected storage
 * (`/data/user_de/<user>/<package>`) is readable from the moment a process starts, so it is the
 * primary location for runtime state; the credential-protected directory is still read as a
 * fallback for state written by earlier versions.
 */
object XposedRuntimeLocations {

    /**
     * Data directories of the app that owns [dataDirectory], the one that survives a reboot first.
     */
    fun dataDirectories(dataDirectory: String): List<String> {
        val normalized = normalize(dataDirectory) ?: return emptyList()
        return listOfNotNull(
            deviceProtectedDataDirectory(normalized),
            credentialProtectedDataDirectory(normalized) ?: normalized,
        ).distinct()
    }

    /** Runtime directories of the app that owns [dataDirectory], primary one first. */
    fun runtimeDirectories(dataDirectory: String): List<File> =
        dataDirectories(dataDirectory).map { File(it, XposedConstants.XPOSED_DIR) }

    /** [fileName] inside every runtime directory of [dataDirectory]'s app, primary one first. */
    fun runtimeFiles(dataDirectory: String, fileName: String): List<File> =
        runtimeDirectories(dataDirectory).map { File(it, fileName) }

    /** Device-protected twin of [dataDirectory], or `null` for an unrecognised data directory. */
    fun deviceProtectedDataDirectory(dataDirectory: String): String? {
        val normalized = normalize(dataDirectory) ?: return null
        return when {
            normalized.startsWith(DEVICE_PROTECTED_ROOT) -> normalized
            normalized.startsWith(CREDENTIAL_PROTECTED_ROOT) ->
                DEVICE_PROTECTED_ROOT + normalized.removePrefix(CREDENTIAL_PROTECTED_ROOT)

            normalized.startsWith(LEGACY_CREDENTIAL_PROTECTED_ROOT) ->
                "$DEVICE_PROTECTED_ROOT$SYSTEM_USER_ID/" +
                    normalized.removePrefix(LEGACY_CREDENTIAL_PROTECTED_ROOT)

            else -> null
        }
    }

    /** Credential-protected twin of [dataDirectory], or `null` for an unrecognised directory. */
    fun credentialProtectedDataDirectory(dataDirectory: String): String? {
        val normalized = normalize(dataDirectory) ?: return null
        return when {
            normalized.startsWith(CREDENTIAL_PROTECTED_ROOT) -> normalized
            normalized.startsWith(DEVICE_PROTECTED_ROOT) ->
                CREDENTIAL_PROTECTED_ROOT + normalized.removePrefix(DEVICE_PROTECTED_ROOT)

            normalized.startsWith(LEGACY_CREDENTIAL_PROTECTED_ROOT) ->
                "$CREDENTIAL_PROTECTED_ROOT$SYSTEM_USER_ID/" +
                    normalized.removePrefix(LEGACY_CREDENTIAL_PROTECTED_ROOT)

            else -> null
        }
    }

    private fun normalize(dataDirectory: String): String? =
        dataDirectory.trimEnd('/').takeIf(String::isNotEmpty)

    private const val DEVICE_PROTECTED_ROOT = "/data/user_de/"
    private const val CREDENTIAL_PROTECTED_ROOT = "/data/user/"
    private const val LEGACY_CREDENTIAL_PROTECTED_ROOT = "/data/data/"
    private const val SYSTEM_USER_ID = "0"
}

package com.alice0.capacitor.otp

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Computes the 11-character SMS Retriever app hash.
 *
 * The hash is derived from `SHA-256(packageName + " " + signingCertificateHex)`,
 * truncated to 9 bytes, and Base64-encoded (no padding) to produce 11 characters.
 *
 * **Important:** This helper is mainly for development / debugging. For production
 * builds signed via Google Play App Signing, the hash must be computed from the
 * Play Console's deployment certificate — it cannot be derived at runtime from
 * the debug keystore.
 */
object AppSignatureHelper {

    private const val HASH_TYPE = "SHA-256"
    private const val NUM_HASHED_BYTES = 9
    private const val NUM_BASE64_CHAR = 11

    /**
     * Returns a list of 11-character app signature hashes for [context]'s package.
     *
     * Typically, contains a single entry unless the APK has multiple signers.
     */
    fun getAppSignatures(context: Context): List<String> {
        val signatures = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }

        return signatures?.mapNotNull { sig ->
            val input = "${context.packageName} ${sig.toCharsString()}"
            try {
                val digest = MessageDigest.getInstance(HASH_TYPE)
                digest.update(input.toByteArray(StandardCharsets.UTF_8))
                val hashBytes = digest.digest()
                // Truncate to first 9 bytes, Base64 encode → 11 characters
                val base64 = Base64.encodeToString(
                    hashBytes.copyOf(NUM_HASHED_BYTES),
                    Base64.NO_PADDING or Base64.NO_WRAP
                )
                base64.substring(0, NUM_BASE64_CHAR)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
    }
}

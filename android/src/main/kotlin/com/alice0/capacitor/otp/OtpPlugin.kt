package com.alice0.capacitor.otp

import android.app.Activity
import android.content.IntentFilter
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Capacitor plugin for automatic OTP SMS reading on Android.
 *
 * Supports two modes:
 * - **User Consent** (default): works with any SMS containing a 4–10 char code.
 *   Shows a system bottom-sheet asking the user to share the SMS.
 * - **SMS Retriever**: fully automatic (no user interaction), but requires the SMS
 *   to end with the 11-character app hash and be ≤ 140 bytes.
 *
 * No `READ_SMS` permission is required — this uses the Google Play Services
 * SMS Retriever / User Consent APIs which are the sanctioned approach.
 */
@CapacitorPlugin(name = "Otp")
class OtpPlugin : Plugin() {

    companion object {
        private const val TAG = "OtpPlugin"
    }

    @Volatile
    private var receiver: SmsBroadcastReceiver? = null

    @Volatile
    private var isListening: Boolean = false

    private var otpPattern: String = "(\\d{4,8})"
    private var currentMode: String = "consent"
    private var savedCallId: String? = null

    // -----------------------------------------------------------------------
    // Plugin Methods
    // -----------------------------------------------------------------------

    /**
     * Pre-flight check: is SMS OTP detection supported on this device?
     */
    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val gmsAvailability = GoogleApiAvailability.getInstance()
        val resultCode = gmsAvailability.isGooglePlayServicesAvailable(context)
        val mode = call.getString("mode")
            ?: config.getString("defaultMode", "consent")
            ?: "consent"

        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                call.resolve(JSObject().apply {
                    put("available", true)
                    put("method", mode)
                })
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                call.resolve(JSObject().apply {
                    put("available", false)
                    put("reason", "GMS_UPDATE_REQUIRED")
                })
            }
            else -> {
                call.resolve(JSObject().apply {
                    put("available", false)
                    put("reason", "GMS_UNAVAILABLE")
                })
            }
        }
    }

    /**
     * Begin listening for incoming OTP SMS.
     *
     * Options:
     * - `mode`: `"consent"` (default) or `"retriever"`
     * - `senderPhoneNumber`: filter by sender (consent mode only)
     * - `otpPattern`: regex with one capture group (default `(\d{4,8})`)
     */
    @PluginMethod
    fun startListening(call: PluginCall) {
        // Read options — fall back to plugin config, then to defaults
        currentMode = call.getString("mode")
            ?: config.getString("defaultMode", "consent")
                    ?: "consent"

        val sender = call.getString("senderPhoneNumber")

        otpPattern = call.getString("otpPattern")
            ?: config.getString("otpPattern", "(\\d{4,8})")
                    ?: "(\\d{4,8})"

        // Clean up any existing receiver first (prevents stacking)
        unregisterReceiver()

        // Create and register broadcast receiver
        receiver = SmsBroadcastReceiver().apply {
            onResult = { result -> handleSmsResult(result) }
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            SmsRetriever.SEND_PERMISSION, // Security: only GMS can broadcast
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Start the appropriate SMS API
        val client = SmsRetriever.getClient(activity)
        val task = if (currentMode == "retriever") {
            client.startSmsRetriever()
        } else {
            client.startSmsUserConsent(sender)
        }

        task.addOnSuccessListener {
            isListening = true
            call.setKeepAlive(true)
            bridge.saveCall(call)
            savedCallId = call.callbackId
            call.resolve()
        }.addOnFailureListener { e ->
            unregisterReceiver()
            val error = JSObject().apply {
                put("code", "API_FAILURE")
                put("message", "Failed to start SMS listener: ${e.message}")
            }
            call.reject("Failed to start SMS listener: ${e.message}", "API_FAILURE", e)
            notifyListeners("otpError", error)
        }
    }

    /**
     * Stop listening and unregister the broadcast receiver.
     */
    @PluginMethod
    fun stopListening(call: PluginCall) {
        unregisterReceiver()
        call.resolve()
    }

    /**
     * Get the 11-character SMS Retriever app hash for this build.
     */
    @PluginMethod
    fun getAppHash(call: PluginCall) {
        val hashes = AppSignatureHelper.getAppSignatures(context)
        if (hashes.isNotEmpty()) {
            call.resolve(JSObject().apply {
                put("hash", hashes.first())
            })
        } else {
            call.reject("Unable to compute app hash", "API_FAILURE")
        }
    }

    // -----------------------------------------------------------------------
    // SMS Result Handling
    // -----------------------------------------------------------------------

    /**
     * Central handler for all [SmsBroadcastReceiver.SmsResult] outcomes.
     */
    private fun handleSmsResult(result: SmsBroadcastReceiver.SmsResult) {
        when (result) {
            is SmsBroadcastReceiver.SmsResult.ConsentIntent -> {
                if (currentMode == "retriever") {
                    // Retriever mode received a consent intent — the SMS didn't
                    // match the retriever format (missing <#> prefix, wrong app
                    // hash, or message > 140 bytes). Report the mismatch.
                    Log.w(TAG, "Retriever mode received a consent intent instead " +
                            "of a direct message. The SMS probably doesn't match the " +
                            "retriever format. Ensure the SMS starts with <#>, ends " +
                            "with the 11-char app hash, and is ≤ 140 bytes.")
                    val error = JSObject().apply {
                        put("code", "API_FAILURE")
                        put("message",
                            "SMS did not match retriever format. Ensure the SMS " +
                                    "starts with <#>, ends with the app hash from " +
                                    "getAppHash(), and is ≤ 140 bytes.")
                    }
                    notifyListeners("otpError", error)
                    unregisterReceiver()
                    return
                }

                // User Consent mode: launch the consent bottom sheet
                val callId = savedCallId
                val savedCall = if (callId != null) bridge.getSavedCall(callId) else null
                if (savedCall != null) {
                    startActivityForResult(savedCall, result.intent, "onSmsConsentResult")
                } else {
                    Log.w(TAG, "No saved call found for consent intent")
                    val error = JSObject().apply {
                        put("code", "API_FAILURE")
                        put("message", "Internal error: unable to launch consent dialog")
                    }
                    notifyListeners("otpError", error)
                    unregisterReceiver()
                }
            }

            is SmsBroadcastReceiver.SmsResult.Message -> {
                // Retriever mode: got the SMS directly — no user interaction
                val otp = OtpParser.extract(result.fullMessage, otpPattern)
                val data = JSObject().apply {
                    put("message", result.fullMessage)
                    if (otp != null) put("otp", otp)
                }
                notifyListeners("otpReceived", data)
                unregisterReceiver()
            }

            is SmsBroadcastReceiver.SmsResult.Timeout -> {
                notifyListeners("otpTimeout", JSObject())
                unregisterReceiver()
            }

            is SmsBroadcastReceiver.SmsResult.Error -> {
                val data = JSObject().apply {
                    put("code", "API_FAILURE")
                    put("message", "SMS broadcast error: status code ${result.statusCode}")
                }
                notifyListeners("otpError", data)
                unregisterReceiver()
            }
        }
    }

    /**
     * Activity callback for the User Consent bottom-sheet result.
     */
    @ActivityCallback
    private fun onSmsConsentResult(call: PluginCall?, result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            if (message != null) {
                val otp = OtpParser.extract(message, otpPattern)
                val data = JSObject().apply {
                    put("message", message)
                    if (otp != null) put("otp", otp)
                }
                notifyListeners("otpReceived", data)
            }
        } else {
            val data = JSObject().apply {
                put("code", "USER_DENIED_CONSENT")
                put("message", "User dismissed the SMS consent dialog")
            }
            notifyListeners("otpError", data)
        }
        call?.setKeepAlive(false)
        unregisterReceiver()
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun handleOnDestroy() {
        unregisterReceiver()
        super.handleOnDestroy()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Safely unregister the broadcast receiver and clean up state.
     */
    private fun unregisterReceiver() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered or already unregistered
        }
        receiver?.onResult = null
        receiver = null
        isListening = false
        savedCallId?.let { id ->
            bridge?.getSavedCall(id)?.let { saved ->
                saved.setKeepAlive(false)
                bridge.releaseCall(saved)
            }
        }
        savedCallId = null
    }
}

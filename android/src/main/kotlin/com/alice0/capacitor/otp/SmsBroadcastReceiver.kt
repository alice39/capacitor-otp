package com.alice0.capacitor.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * BroadcastReceiver that handles SMS Retriever and User Consent broadcasts
 * from Google Play Services.
 *
 * It is stateless — all state lives in [OtpPlugin]. The [onResult] lambda
 * is set by the plugin after creating the receiver instance.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    /**
     * Sealed class representing the possible broadcast outcomes.
     */
    sealed class SmsResult {
        /** User Consent mode: an intent to launch the consent bottom sheet. */
        data class ConsentIntent(val intent: Intent) : SmsResult()
        /** Retriever mode: the full SMS message text. */
        data class Message(val fullMessage: String) : SmsResult()
        /** The 5-minute listening window expired without receiving an SMS. */
        object Timeout : SmsResult()
        /** An error occurred (non-success, non-timeout status code). */
        data class Error(val statusCode: Int) : SmsResult()
    }

    /** Callback set by [OtpPlugin] to handle broadcast results. */
    var onResult: ((SmsResult) -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return

        val extras: Bundle = intent.extras ?: return
        val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return

        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                // User Consent mode: EXTRA_CONSENT_INTENT is present
                @Suppress("DEPRECATION")
                val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                if (consentIntent != null) {
                    onResult?.invoke(SmsResult.ConsentIntent(consentIntent))
                    return
                }
                // Retriever mode: EXTRA_SMS_MESSAGE contains full text
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                if (message != null) {
                    onResult?.invoke(SmsResult.Message(message))
                }
            }
            CommonStatusCodes.TIMEOUT -> {
                onResult?.invoke(SmsResult.Timeout)
            }
            else -> {
                onResult?.invoke(SmsResult.Error(status.statusCode))
            }
        }
    }
}

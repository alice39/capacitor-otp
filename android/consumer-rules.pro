# Google Play Services SMS Retriever / User Consent
-keep class com.google.android.gms.auth.api.phone.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# Capacitor plugin reflection
-keep class com.alice0.capacitor.otp.** { *; }
-keepclassmembers class com.alice0.capacitor.otp.OtpPlugin {
    @com.getcapacitor.PluginMethod *;
    @com.getcapacitor.annotation.ActivityCallback *;
}

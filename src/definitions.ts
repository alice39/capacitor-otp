import type { PluginListenerHandle } from '@capacitor/core';

// ---------------------------------------------------------------------------
// Error Codes
// ---------------------------------------------------------------------------

export enum OtpErrorCode {
  /** Google Play Services not available on this device */
  GMS_UNAVAILABLE = 'GMS_UNAVAILABLE',
  /** Google Play Services version too old */
  GMS_UPDATE_REQUIRED = 'GMS_UPDATE_REQUIRED',
  /** User denied the SMS consent bottom sheet */
  USER_DENIED_CONSENT = 'USER_DENIED_CONSENT',
  /** SmsRetriever/UserConsent API call failed */
  API_FAILURE = 'API_FAILURE',
  /** Listener already active — call stopListening() first */
  ALREADY_LISTENING = 'ALREADY_LISTENING',
  /** Platform does not support this feature */
  UNSUPPORTED = 'UNSUPPORTED',
  /** Unknown error */
  UNKNOWN = 'UNKNOWN',
}

// ---------------------------------------------------------------------------
// Options & Result Types
// ---------------------------------------------------------------------------

export interface StartListeningOptions {
  /**
   * Android listening mode.
   * - `'consent'` — SMS User Consent API (default). Works with any SMS containing a 4–10 char code.
   * - `'retriever'` — SMS Retriever API. Requires the SMS to end with the 11-char app hash.
   */
  mode?: 'consent' | 'retriever';
  /**
   * (Android, consent mode only) Filter SMS from this sender phone number.
   * If omitted, any sender matches.
   */
  senderPhoneNumber?: string;
  /**
   * Regex pattern used to extract the OTP from the SMS body.
   * Must contain exactly one capture group. Default: `(\d{4,8})`
   */
  otpPattern?: string;
  /**
   * Enable clipboard-based fallback on iOS.
   * Defaults to `true` on iOS 15, `false` on iOS 16+ (paste banner).
   */
  enableClipboardFallback?: boolean;
}

export interface OtpReceivedEvent {
  /** Full SMS text (Android) or clipboard content (iOS). */
  message: string;
  /** Extracted OTP if the regex matched, otherwise `undefined`. */
  otp?: string;
}

export interface OtpErrorEvent {
  /** Machine-readable error code. */
  code: OtpErrorCode;
  /** Human-readable description. */
  message: string;
}

export type OtpTimeoutEvent = {
  // empty – the 5-minute window expired
};

export interface AppHashResult {
  /** The 11-character SMS Retriever app hash (Android only). */
  hash: string;
}

export interface AvailabilityResult {
  /** Whether OTP detection is supported on this device / OS version. */
  available: boolean;
  /** The detection method that would be used. */
  method?: 'consent' | 'retriever' | 'clipboard';
  /** Human-readable reason when `available` is `false`. */
  reason?: string;
}

// ---------------------------------------------------------------------------
// Common OTP regex pattern constants
// ---------------------------------------------------------------------------

/** Matches 4–8 digit numeric OTP codes. */
export const PATTERN_NUMERIC = '(\\d{4,8})';
/** Matches 4–10 character alphanumeric OTP codes. */
export const PATTERN_ALPHANUMERIC = '([A-Za-z0-9]{4,10})';

// ---------------------------------------------------------------------------
// Plugin Interface
// ---------------------------------------------------------------------------

export interface OtpPlugin {
  /**
   * Pre-flight check: is SMS OTP detection supported on this device?
   */
  isAvailable(): Promise<AvailabilityResult>;

  /**
   * Begin listening for incoming OTP SMS.
   *
   * **Android:** Starts the SMS User Consent or SMS Retriever API.
   * **iOS:** Starts clipboard monitoring (version-gated).
   */
  startListening(options?: StartListeningOptions): Promise<void>;

  /**
   * Stop listening and unregister receivers / timers.
   */
  stopListening(): Promise<void>;

  /**
   * Get the 11-character SMS Retriever app hash (Android only).
   * Throws `unimplemented` on iOS and Web.
   */
  getAppHash(): Promise<AppHashResult>;

  /**
   * Subscribe to plugin events.
   */
  addListener(eventName: 'otpReceived', listenerFunc: (event: OtpReceivedEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'otpTimeout', listenerFunc: (event: OtpTimeoutEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'otpError', listenerFunc: (event: OtpErrorEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners for this plugin.
   */
  removeAllListeners(): Promise<void>;
}

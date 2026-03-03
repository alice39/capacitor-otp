import { WebPlugin } from '@capacitor/core';

import type {
  OtpPlugin,
  StartListeningOptions,
  AppHashResult,
  AvailabilityResult,
} from './definitions';

/**
 * Web implementation of OtpPlugin.
 *
 * SMS reading is not possible in browsers. All methods throw `unimplemented`.
 */
export class OtpWeb extends WebPlugin implements OtpPlugin {
  async isAvailable(): Promise<AvailabilityResult> {
    return { available: false, reason: 'UNSUPPORTED' };
  }

  async startListening(_options?: StartListeningOptions): Promise<void> {
    throw this.unimplemented('SMS OTP listening is not available on the web.');
  }

  async stopListening(): Promise<void> {
    throw this.unimplemented('SMS OTP listening is not available on the web.');
  }

  async getAppHash(): Promise<AppHashResult> {
    throw this.unimplemented('App hash is an Android-only feature.');
  }
}

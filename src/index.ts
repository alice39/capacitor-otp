import { registerPlugin } from '@capacitor/core';

import type { OtpPlugin } from './definitions';

const Otp = registerPlugin<OtpPlugin>('Otp', {
  web: () => import('./web').then((m) => new m.OtpWeb()),
});

export * from './definitions';
export { Otp };

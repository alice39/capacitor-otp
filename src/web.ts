import { WebPlugin } from '@capacitor/core';

import type { OtpPlugin } from './definitions';

export class OtpWeb extends WebPlugin implements OtpPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

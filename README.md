# capacitor-otp

Automatic OTP SMS reading for Capacitor apps on iOS and Android under MIT license.

## Features

- **Android**: Google Play Services SMS Retriever (automatic) or User Consent API (with user interaction)
- **iOS**: Automatic detection with clipboard fallback option
- No `READ_SMS` permission required — uses modern, privacy-respecting APIs
- Regex-based OTP extraction with customizable patterns
- Event-based API with error and timeout handling

## Installation

```bash
npm install github:alice39/capacitor-otp
npx cap sync
```

## Quick Start

```typescript
import { Otp } from 'capacitor-otp';

// Start listening for OTPs
const listener = await Otp.startListening({
	mode: 'consent', // Android: 'consent' or 'retriever'
	otpPattern: '(\\d{6})', // Extract 6-digit code
});

// Handle OTP received
listener.on('otpReceived', (event) => {
	console.log('OTP:', event.otp);
});

// Handle errors
listener.on('otpError', (event) => {
	console.error('Error:', event.message);
});

// Stop listening
await Otp.stopListening();
```

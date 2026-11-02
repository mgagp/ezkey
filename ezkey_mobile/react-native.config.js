/**
 * React Native CLI configuration.
 *
 * iOS does not link the ML Kit barcode scanner: Google ML Kit ships no arm64
 * simulator slice, and iOS decodes enrollment QR codes with VisionCamera's native
 * AVFoundation object output instead (app/components/enrollmentCodeOutput.ios.ts).
 * Android keeps ML Kit (enrollmentCodeOutput.android.ts).
 */
module.exports = {
  dependencies: {
    'react-native-vision-camera-barcode-scanner': {
      platforms: {
        ios: null,
      },
    },
  },
};

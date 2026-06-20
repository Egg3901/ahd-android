import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.ahousedivided.app',
  appName: 'A House Divided',
  webDir: 'www',
  android: {
    allowMixedContent: false,
    // The WebView loads the remote URL (see server.url).
    // This keeps the domain-scoped JWT cookie working without
    // reimplementing auth.
    captureInput: true,
    backgroundColor: '#0f172a',
  },
  server: {
    // Load the live production site. The WebView will be restricted
    // to this origin so cookies flow naturally.
    url: 'https://www.ahousedividedgame.com',
    cleartext: false, // enforce HTTPS
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1500,
      backgroundColor: '#0f172a',
      showSpinner: false,
      androidScaleType: 'CENTER_CROP',
    },
  },
};

export default config;
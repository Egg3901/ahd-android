import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.ahousedivided.app',
  appName: 'A House Divided',
  webDir: 'www',
  android: {
    allowMixedContent: false,
    captureInput: true,
    backgroundColor: '#0f172a',
    // Appended to the WebView UA so the server can detect native app context
    // and suppress web chrome (navbar, statusbar, footer) that we replace natively.
    appendUserAgent: 'AHD-Android/1.0',
  },
  server: {
    // Load the live production site. The WebView will be restricted
    // to this origin so cookies flow naturally.
    url: 'https://www.ahousedividedgame.com',
    cleartext: false, // enforce HTTPS
    // Allow OAuth provider domains to navigate inside the WebView.
    // Without this, OAuth callback redirects set the session cookie
    // in the system browser's cookie jar instead of the WebView's,
    // and the user never gets logged in inside the app.
    allowNavigation: [
      'ahousedividedgame.com',
      'discord.com',
      'accounts.google.com',
      'www.google.com',
    ],
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
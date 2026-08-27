import type { CapacitorConfig } from '@capacitor/cli';
// Needs the import attribute: the Capacitor CLI loads this file as an ES
// module, and Node refuses a JSON import without `with { type: 'json' }`.
// Without it `cap sync` dies with ERR_IMPORT_ATTRIBUTE_MISSING and exits 1,
// which leaves the previous synced assets in place — the Gradle build still
// succeeds against stale output, so the failure is easy to miss locally.
import packageJson from './package.json' with { type: 'json' };

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
    appendUserAgent: `AHD-Android/${packageJson.version}`,
  },
  server: {
    // Load the live production site. The WebView will be restricted
    // to this origin so cookies flow naturally. Apex, not www — the
    // server 301s www -> apex, and that extra hop on every cold start
    // is one more thing that can fail on a flaky mobile connection.
    url: 'https://ahousedividedgame.com',
    cleartext: false, // enforce HTTPS
    // Allow OAuth provider domains to navigate inside the WebView.
    // Without this, OAuth callback redirects set the session cookie
    // in the system browser's cookie jar instead of the WebView's,
    // and the user never gets logged in inside the app.
    allowNavigation: [
      'ahousedividedgame.com',
      // The Discord OAuth callback (DISCORD_REDIRECT_URI) lands on the www
      // host before the server 301s to apex. Without www here the WebView
      // punts that callback to the system browser, the session cookie is set
      // there instead of in the app, and the user is "signed in" only in the
      // browser — the reported "redirects to browser after Discord auth" bug.
      'www.ahousedividedgame.com',
      'discord.com',
      'accounts.google.com',
      'www.google.com',
    ],
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1500,
      launchFadeOutDuration: 500,
      backgroundColor: '#0f172a',
      showSpinner: false,
      androidScaleType: 'CENTER_INSIDE',
    },
  },
};

export default config;
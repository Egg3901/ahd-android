/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Push Notifications — Phase 2 Stub
 *
 * This file is loaded by the WebView via Capacitor's JS bridge.
 * It registers the device for FCM push notifications and sends
 * the token to the game server's push registration endpoint.
 *
 * USAGE:
 *   1. Add `google-services.json` to android/app/
 *   2. Add Firebase project, get FCM credentials
 *   3. Uncomment the registration call in the game's web app
 *      or inject this script via Capacitor's JS injection
 *
 * SERVER-SIDE HOOK (game's hourly turn processor):
 *   When a turn advances or a vote is scheduled, the server calls
 *   FCM with the player's stored device token:
 *
 *   POST https://fcm.googleapis.com/fcm/send
 *   Headers: { Authorization: `key=${FCM_SERVER_KEY}` }
 *   Body: {
 *     "to": "<player_device_token>",
 *     "notification": {
 *       "title": "Your Turn",
 *       "body": "A new turn has started in A House Divided.",
 *       "click_action": "FLUTTER_NOTIFICATION_CLICK",
 *     },
 *     "data": {
 *       "type": "turn_start",
 *       "turn": 1234,
 *       "deep_link": "https://www.ahousedividedgame.com/dashboard"
 *     }
 *   }
 *
 * The game server should store device tokens in the user document:
 *   user.pushTokens: [{ token, platform: "android", createdAt, lastSeen }]
 *
 * Register tokens via:
 *   POST /api/push/register { token, platform: "android" }
 * Unregister via:
 *   POST /api/push/unregister { token }
 *
 * The server's hourly cron processor should:
 *   1. After turn advancement, query users who have pushTokens
 *   2. Filter by notification preferences (user.settings.pushNotifications)
 *   3. Batch-send FCM messages (batch API supports up to 500 tokens)
 *   4. Handle invalid token responses (remove stale tokens)
 */

async function registerPushNotifications(): Promise<void> {
  const w = window as any;
  if (!w.Capacitor || !w.Capacitor.isNativePlatform()) {
    console.log('[AHD Push] Not running on native platform, skipping registration');
    return;
  }

  const { PushNotifications } = await import('@capacitor/push-notifications');

  try {
    // Request permission
    let permStatus = await PushNotifications.checkPermissions();
    if (permStatus.receive === 'prompt') {
      permStatus = await PushNotifications.requestPermissions();
    }
    if (permStatus.receive !== 'granted') {
      console.log('[AHD Push] Permission not granted');
      return;
    }

    // Register with FCM
    await PushNotifications.register();

    // Listen for registration token
    PushNotifications.addListener('registration', async (token: any) => {
      console.log('[AHD Push] Device registered:', token.value.substring(0, 20) + '...');

      // Send token to game server
      try {
        await fetch('/api/push/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            token: token.value,
            platform: 'android',
          }),
        });
      } catch (err) {
        console.error('[AHD Push] Failed to register with server:', err);
      }
    });

    // Listen for registration errors
    PushNotifications.addListener('registrationError', (err: any) => {
      console.error('[AHD Push] Registration error:', err);
    });

    // Listen for push notifications received (foreground)
    PushNotifications.addListener('pushNotificationReceived', (notification: any) => {
      console.log('[AHD Push] Notification received:', notification);
    });

    // Listen for notification tap (user taps notification to open app)
    PushNotifications.addListener('pushNotificationActionPerformed', (action: any) => {
      console.log('[AHD Push] Notification tapped:', action);
      const data = action.notification?.data;
      if (data?.deep_link) {
        window.location.href = data.deep_link;
      }
    });
  } catch (err) {
    console.error('[AHD Push] Setup failed:', err);
  }
}

// Export for manual triggering if needed
if (typeof window !== 'undefined') {
  (window as any).AHD_PushNotifications = { register: registerPushNotifications };
}
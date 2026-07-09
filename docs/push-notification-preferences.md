# Push notification preference contract

Client reference: [`src/push-notifications.ts`](../src/push-notifications.ts)

## Product rules

1. Notifications are **opt-in**. Default is off.
2. Never register FCM or call `/api/push/register` on app launch unless the player previously opted in.
3. The Settings toggle is the product preference. The Android system prompt is separate.
4. Opt-out must always work from Settings, even if the server call fails (clear local preference + unregister FCM locally).

## Web app API (`window.AHD_PushNotifications`)

| Method | When to call | Side effects |
|--------|--------------|--------------|
| `getStatus()` | Render Settings toggle | None |
| `enable()` | Toggle ON | OS permission → FCM register → `POST /api/push/register` `{ enabled: true }` |
| `disable()` | Toggle OFF | `POST /api/push/unregister` `{ enabled: false }` → FCM unregister |
| `syncIfOptedIn()` | Native cold start / resume | Refresh token only if already opted in; never prompts |

### `getStatus()` shape

```ts
{
  optedIn: boolean;      // local product preference (default false)
  permission: 'prompt' | 'granted' | 'denied' | 'unavailable';
  hasToken: boolean;
  isNative: boolean;
}
```

### Suggested Settings UX

- Toggle bound to `status.optedIn`
- On enable failure with `permission_denied`: leave toggle off; show how to enable notifications in system settings
- On non-native web: hide the toggle or show “available in the Android app”

## Server rules

- `POST /api/push/register` → store token **and** set `settings.pushNotifications = true`
- `POST /api/push/unregister` → remove token **and** set `settings.pushNotifications = false`
- Turn cron may send only when `settings.pushNotifications === true` **and** `pushTokens` is non-empty
- Drop stale tokens when FCM returns `messaging/registration-token-not-registered`

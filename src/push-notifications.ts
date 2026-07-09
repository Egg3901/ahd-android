/**
 * Push Notifications — Phase 2 (opt-in / opt-out)
 *
 * Explicit preference API for the game web app. Notifications stay OFF until
 * the player opts in. Never auto-register on load.
 *
 * Web app usage (Settings → Notifications toggle):
 *   const push = window.AHD_PushNotifications;
 *   const status = await push.getStatus();          // drive the toggle
 *   await push.enable();                            // opt in
 *   await push.disable();                           // opt out
 *   await push.syncIfOptedIn();                     // cold start only
 *
 * Setup still required before this does anything at runtime:
 *   1. Add `google-services.json` to android/app/
 *   2. Firebase project + FCM credentials on the game server
 *   3. Load/inject this module from the web app (or Capacitor bridge)
 *
 * Server contract:
 *   POST /api/push/register   { token, platform: "android", enabled: true }
 *     → store token; set user.settings.pushNotifications = true
 *   POST /api/push/unregister { token, enabled: false }
 *     → remove token; set user.settings.pushNotifications = false
 *
 * Cron must only notify users where:
 *   settings.pushNotifications === true AND pushTokens is non-empty
 *
 * FCM send path: HTTP v1 (Admin SDK). Legacy Server Key API is shut down.
 */

const PREF_KEY = 'ahd.pushNotifications.optedIn';
const TOKEN_KEY = 'ahd.pushNotifications.token';
const LOG = '[AHD Push]';

export type PushPermission = 'prompt' | 'granted' | 'denied' | 'unavailable';

export interface PushStatus {
  /** Product preference (local). Default false — never opt in silently. */
  optedIn: boolean;
  /** OS notification permission. */
  permission: PushPermission;
  /** Last known FCM token, if any. */
  hasToken: boolean;
  /** Native Capacitor shell present. */
  isNative: boolean;
}

export interface PushEnableResult {
  ok: boolean;
  status: PushStatus;
  reason?: 'not_native' | 'permission_denied' | 'registration_failed' | 'server_error';
}

export interface PushDisableResult {
  ok: boolean;
  status: PushStatus;
  reason?: 'not_native' | 'unregister_failed';
}

type PushPlugin = typeof import('@capacitor/push-notifications').PushNotifications;
type PluginHandle = { remove: () => Promise<void> };

let listenersAttached = false;
let listenerHandles: PluginHandle[] = [];
let pendingTokenResolve: ((token: string) => void) | null = null;
let pendingTokenReject: ((err: Error) => void) | null = null;

function isNativePlatform(): boolean {
  const w = window as Window & {
    Capacitor?: { isNativePlatform?: () => boolean };
  };
  return Boolean(w.Capacitor?.isNativePlatform?.());
}

function readOptedIn(): boolean {
  try {
    return localStorage.getItem(PREF_KEY) === 'true';
  } catch {
    return false;
  }
}

function writeOptedIn(value: boolean): void {
  try {
    if (value) localStorage.setItem(PREF_KEY, 'true');
    else localStorage.removeItem(PREF_KEY);
  } catch {
    // private mode / storage blocked — preference still enforced in-memory via callers
  }
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function writeStoredToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
}

async function loadPlugin(): Promise<PushPlugin | null> {
  if (!isNativePlatform()) return null;
  const { PushNotifications } = await import('@capacitor/push-notifications');
  return PushNotifications;
}

async function ensureListeners(plugin: PushPlugin): Promise<void> {
  if (listenersAttached) return;

  const registration = await plugin.addListener('registration', (token) => {
    const value = token.value;
    console.log(LOG, 'Device registered:', value.substring(0, 20) + '...');
    writeStoredToken(value);
    // Server register/unregister is owned by enable() / disable() /
    // syncIfOptedIn() so we never double-post or race an opt-out.
    if (pendingTokenResolve) {
      pendingTokenResolve(value);
      pendingTokenResolve = null;
      pendingTokenReject = null;
    }
  });

  const registrationError = await plugin.addListener('registrationError', (err) => {
    console.error(LOG, 'Registration error:', err);
    if (pendingTokenReject) {
      pendingTokenReject(new Error(err.error ?? 'registration_failed'));
      pendingTokenResolve = null;
      pendingTokenReject = null;
    }
  });

  const received = await plugin.addListener('pushNotificationReceived', (notification) => {
    console.log(LOG, 'Notification received:', notification);
  });

  const action = await plugin.addListener('pushNotificationActionPerformed', (event) => {
    console.log(LOG, 'Notification tapped:', event);
    const data = event.notification?.data as { deep_link?: string } | undefined;
    if (data?.deep_link) {
      window.location.href = data.deep_link;
    }
  });

  listenerHandles = [registration, registrationError, received, action];
  listenersAttached = true;
}

async function postRegister(token: string): Promise<void> {
  const res = await fetch('/api/push/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      token,
      platform: 'android',
      enabled: true,
    }),
  });
  if (!res.ok) {
    throw new Error(`register_http_${res.status}`);
  }
}

async function postUnregister(token: string): Promise<void> {
  const res = await fetch('/api/push/unregister', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      token,
      enabled: false,
    }),
  });
  if (!res.ok) {
    throw new Error(`unregister_http_${res.status}`);
  }
}

function waitForToken(timeoutMs = 15000): Promise<string> {
  return new Promise((resolve, reject) => {
    // Always wait for a fresh `registration` event after plugin.register().
    const timer = window.setTimeout(() => {
      pendingTokenResolve = null;
      pendingTokenReject = null;
      reject(new Error('registration_timeout'));
    }, timeoutMs);

    pendingTokenResolve = (token) => {
      window.clearTimeout(timer);
      resolve(token);
    };
    pendingTokenReject = (err) => {
      window.clearTimeout(timer);
      reject(err);
    };
  });
}

async function permissionOf(plugin: PushPlugin | null): Promise<PushPermission> {
  if (!plugin) return 'unavailable';
  try {
    const status = await plugin.checkPermissions();
    return status.receive as PushPermission;
  } catch {
    return 'unavailable';
  }
}

async function getStatus(): Promise<PushStatus> {
  const native = isNativePlatform();
  const plugin = native ? await loadPlugin() : null;
  return {
    optedIn: readOptedIn(),
    permission: await permissionOf(plugin),
    hasToken: Boolean(readStoredToken()),
    isNative: native,
  };
}

/**
 * Explicit opt-in. Requests OS permission, registers FCM, and tells the server.
 * Safe to call from a Settings toggle — does nothing useful on plain web.
 */
async function enable(): Promise<PushEnableResult> {
  const plugin = await loadPlugin();
  if (!plugin) {
    return { ok: false, status: await getStatus(), reason: 'not_native' };
  }

  try {
    await ensureListeners(plugin);

    let perm = await plugin.checkPermissions();
    if (perm.receive === 'prompt') {
      perm = await plugin.requestPermissions();
    }
    if (perm.receive !== 'granted') {
      writeOptedIn(false);
      return { ok: false, status: await getStatus(), reason: 'permission_denied' };
    }

    // Persist preference only after OS permission is granted.
    writeOptedIn(true);

    const tokenPromise = waitForToken();
    await plugin.register();
    const token = await tokenPromise;

    try {
      await postRegister(token);
    } catch (err) {
      console.error(LOG, 'Server register failed after opt-in:', err);
      return { ok: false, status: await getStatus(), reason: 'server_error' };
    }

    return { ok: true, status: await getStatus() };
  } catch (err) {
    console.error(LOG, 'Enable failed:', err);
    writeOptedIn(false);
    return { ok: false, status: await getStatus(), reason: 'registration_failed' };
  }
}

/**
 * Explicit opt-out. Unregisters the token with the server and FCM, clears preference.
 */
async function disable(): Promise<PushDisableResult> {
  const plugin = await loadPlugin();
  const token = readStoredToken();

  // Clear product preference first so any in-flight registration listener
  // will not re-register with the server.
  writeOptedIn(false);

  if (!plugin) {
    writeStoredToken(null);
    return { ok: true, status: await getStatus(), reason: 'not_native' };
  }

  try {
    if (token) {
      try {
        await postUnregister(token);
      } catch (err) {
        console.error(LOG, 'Server unregister failed (continuing local opt-out):', err);
      }
    }

    await plugin.unregister();
    writeStoredToken(null);
    return { ok: true, status: await getStatus() };
  } catch (err) {
    console.error(LOG, 'Disable failed:', err);
    writeStoredToken(null);
    return { ok: false, status: await getStatus(), reason: 'unregister_failed' };
  }
}

/**
 * Cold-start helper: if the player previously opted in and OS permission is
 * still granted, refresh the FCM token with the server. Never prompts and
 * never opts the user in.
 */
async function syncIfOptedIn(): Promise<PushStatus> {
  if (!readOptedIn()) {
    return getStatus();
  }

  const plugin = await loadPlugin();
  if (!plugin) {
    return getStatus();
  }

  try {
    await ensureListeners(plugin);
    const perm = await plugin.checkPermissions();
    if (perm.receive !== 'granted') {
      // OS permission revoked — treat as opted out locally; server still has
      // settings.pushNotifications until they open Settings, but we won't
      // re-prompt. Clear local preference so the toggle shows off.
      writeOptedIn(false);
      writeStoredToken(null);
      return getStatus();
    }

    const tokenPromise = waitForToken();
    await plugin.register();
    const token = await tokenPromise;
    try {
      await postRegister(token);
    } catch (err) {
      console.error(LOG, 'syncIfOptedIn server register failed:', err);
    }
  } catch (err) {
    console.error(LOG, 'syncIfOptedIn failed:', err);
  }

  return getStatus();
}

export const AHD_PushNotifications = {
  getStatus,
  enable,
  disable,
  syncIfOptedIn,
};

declare global {
  interface Window {
    AHD_PushNotifications: typeof AHD_PushNotifications;
  }
}

if (typeof window !== 'undefined') {
  window.AHD_PushNotifications = AHD_PushNotifications;
}

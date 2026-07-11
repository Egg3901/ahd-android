# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 1.1.x   | Yes       |
| < 1.1   | No        |

Security fixes are released on the latest minor version. See [CHANGELOG.md](CHANGELOG.md)
for release history.

## Reporting a vulnerability

**Please do not open public GitHub issues for security vulnerabilities.**

Report security issues privately to the maintainers:

- **Email:** security@ahousedividedgame.com (replace with your operational inbox)
- **Subject:** `[AHD Android Security]` short summary

Include:

1. Affected version(s) and build type (debug vs release)
2. Steps to reproduce
3. Impact assessment (confidentiality, integrity, availability)
4. Proof of concept, if available

We aim to acknowledge reports within **3 business days** and provide a remediation
timeline within **10 business days** for confirmed issues.

## Security model

This repository is a **thin Capacitor WebView wrapper**. It does not implement game
logic, authentication, or user data storage. Sensitive behavior lives on the remote
web application at `https://www.ahousedividedgame.com`.

### What this app protects

| Control | Implementation |
| ------- | -------------- |
| Transport encryption | HTTPS-only remote URL; `cleartext: false`; `usesCleartextTraffic="false"`; network security config blocks cleartext |
| Session storage | HTTP-only JWT cookie on the game domain (set by the server, not this app) |
| Backup exfiltration | `android:allowBackup="false"` |
| Debug WebView inspection | Enabled only when `FLAG_DEBUGGABLE` (debug builds) |
| External navigation | Non-allowlisted hosts open in Custom Tabs, not the WebView |
| File sharing scope | FileProvider limited to `cache-path/shared/` |
| Release hardening | R8 minification + resource shrinking with Capacitor keep rules |

### Known trade-offs

These are **documented architectural choices**, not oversights:

1. **OAuth inside the WebView** — Required so OAuth callback redirects set the session
   cookie in the WebView jar. Google Sign-In discourages embedded WebViews; Discord
   OAuth is the primary path. Mitigation: strict `allowNavigation` host list.

2. **Third-party cookies enabled** — Required for some OAuth flows. Scope is limited to
   configured OAuth and game domains.

3. **No certificate pinning** — The WebView trusts the system CA store. Pinning would
   complicate rotation and is uncommon for consumer WebView wrappers. MITM risk exists
   on compromised devices or networks.

4. **Remote content dependency** — XSS or supply-chain issues in the hosted web app affect
   users inside the wrapper. The wrapper cannot sandbox game JavaScript beyond standard
   WebView isolation.

5. **POST_NOTIFICATIONS declared** — Permission is declared for Phase 2 push notifications
   but not yet requested at runtime until FCM is wired.

## Secrets and signing

Never commit:

- `*.keystore`, `*.jks`
- `android/keystore.properties`
- `google-services.json`
- `.env`

Use [android/keystore.properties.example](android/keystore.properties.example) as a
template. Store signing credentials in CI secrets or a password manager, not the repo.

## Dependency hygiene

- Run `npm audit` before releases (`npm run audit`).
- Dependabot opens PRs for npm and GitHub Actions updates.
- Review Capacitor and Android Gradle Plugin release notes when upgrading.

## Secure development checklist

Before each release:

- [ ] `npm ci && npm run typecheck && npm run audit`
- [ ] `./gradlew test` and `./gradlew assembleRelease` locally (with signing config)
- [ ] Verify OAuth login (Discord) and session persistence after app restart
- [ ] Confirm external links open in Custom Tabs, not the WebView
- [ ] Merge the release-please PR and verify `CHANGELOG.md` + version bumps

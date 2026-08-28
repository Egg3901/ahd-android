# Changelog

All notable changes to this project are documented in this file.

## [0.4.1](https://github.com/Egg3901/ahd-android/compare/v0.4.0...v0.4.1) (2026-08-28)


### Bug Fixes

* **config:** add import attribute so cap sync survives the Capacitor 8.5 bump ([124f6ff](https://github.com/Egg3901/ahd-android/commit/124f6ff0c4fdb435ebcea35c8f10b9c421c93f8f))
* **deps:** clear critical tar advisory (note: CI is blocked on billing, not code) ([751834b](https://github.com/Egg3901/ahd-android/commit/751834b3ef9cc36d3fc67b388658d5b0b2e31a75))
* **deps:** clear critical tar and high brace-expansion advisories ([4238a0d](https://github.com/Egg3901/ahd-android/commit/4238a0d6042a7c287e29f1bd434debce0b2be2bd))
* **deps:** pin uuid past the buffer bounds advisory ([#27](https://github.com/Egg3901/ahd-android/issues/27)) ([fd4e168](https://github.com/Egg3901/ahd-android/commit/fd4e168229bb7a0a75b80f3d8573d511740466f0))
* unbreak Android build inputs, clear dependabot backlog ([#22](https://github.com/Egg3901/ahd-android/issues/22)) ([751834b](https://github.com/Egg3901/ahd-android/commit/751834b3ef9cc36d3fc67b388658d5b0b2e31a75))


### Documentation

* **security:** fix reporting channel and supported versions ([#25](https://github.com/Egg3901/ahd-android/issues/25)) ([aad161d](https://github.com/Egg3901/ahd-android/commit/aad161db1effff6dea743a82ae707e01804ea284))


### Continuous Integration

* upload CodeQL results now that code scanning is available ([#24](https://github.com/Egg3901/ahd-android/issues/24)) ([56a5da3](https://github.com/Egg3901/ahd-android/commit/56a5da3b26ad2aeb5c57efedda9f7a0b3309541a))

## [0.4.0]

### Features

- In-app Main/Sandbox server switcher — long-press the top-left corner to point the app at `sandbox.ahousedividedgame.com`; the choice persists across launches.

### Bug Fixes

- Discord OAuth now returns to the app instead of the system browser. The callback lands on `www.ahousedividedgame.com`, which was missing from `allowNavigation`, so the WebView punted it to the browser and the session cookie was set there. Added `www` to the allowlist.


The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release versions and this changelog are managed by
[release-please](https://github.com/googleapis/release-please) from
[Conventional Commits](https://www.conventionalcommits.org/) on `master`.

## [0.3.0](https://github.com/Egg3901/ahd-android/compare/v0.2.0...v0.3.0) (2026-07-11)

### Features

* Network monitoring tracks the default active network instead of every network, so a WiFi-to-cellular handoff no longer flashes a spurious "offline" overlay
* Offline detection now requires a validated internet link, so a captive portal or dead connection is treated as offline rather than falsely "connected"

### Notes

* First public release build, bundled for Firebase App Distribution ahead of the initial launch

## [0.2.0](https://github.com/Egg3901/ahd-android/compare/v1.1.0...v0.2.0) (2026-07-11)

### Fixes

* Launcher icon: restore white fill inside the Liberty Bell ring (icon was washed out against the dark background)
* Restore `ahousedivided` package/domain references that were accidentally blanked out in a prior pass, breaking the instrumented test build
* Enable `BuildConfig` generation so `androidTest` compiles under AGP 8
* First manually-cut release; version renumbered to 0.2.0 to reflect actual release maturity ahead of Play Store submission

## [1.1.0](https://github.com/Egg3901/ahd-android/compare/v1.0.0...v1.1.0) (2026-07-04)

### Features

* Splash screen with Liberty Bell logo, Fraunces wordmark, and fade-out
* Native UX polish for MVP (back navigation, offline overlay, Custom Tabs)

### Bug Fixes

* Splash wordmark uses Geist SemiBold, matching the site nav lockup
* OAuth providers stay in WebView so session cookies persist
* Add bare game domain to `allowNavigation`

### Documentation

* MVP completion handoff prompt

## [1.0.0](https://github.com/Egg3901/ahd-android/releases/tag/v1.0.0) (2026-07-03)

### Features

* Capacitor Android wrapper for A House Divided
* Real Liberty Bell icons and splash sourced from the live site
* Thin WebView wrapper with signed release build setup

### Bug Fixes

* Address initial repo audit findings

### Continuous Integration

* Use Node 22 (required by Capacitor 8 CLI)

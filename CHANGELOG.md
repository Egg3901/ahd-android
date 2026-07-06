# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release versions and this changelog are managed by
[release-please](https://github.com/googleapis/release-please) from
[Conventional Commits](https://www.conventionalcommits.org/) on `master`.

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

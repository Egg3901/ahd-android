# Contributing

Thanks for helping improve the A House Divided Android wrapper.

## Development setup

See [README.md](README.md) for prerequisites, build commands, and project structure.

Quick start:

```bash
npm ci
npx cap sync android
npm run build:apk
```

## Branching and pull requests

1. Branch from `master`.
2. Keep changes focused — this repo is intentionally a thin WebView shell.
3. Open a PR against `master` with a clear description and test notes.
4. Ensure CI passes (build, typecheck, unit tests, dependency audit).

## Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/) so
[release-please](https://github.com/googleapis/release-please) can generate
[CHANGELOG.md](CHANGELOG.md) and semver tags automatically.

```
<type>(<optional scope>): <short description>

[optional body]
```

Common types:

| Type | When to use |
| ---- | ----------- |
| `feat` | New user-facing behavior |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `ci` | CI / workflow changes |
| `build` | Build system or dependencies |
| `refactor` | Code change without behavior change |
| `test` | Tests only |
| `chore` | Maintenance (often hidden from changelog) |
| `security` | Security fix or hardening |

Examples:

```
feat: add double-tap-to-exit on back press
fix: keep OAuth callbacks inside the WebView
docs: document keystore.properties signing flow
ci: run npm audit on pull requests
```

**Do not** hand-edit version numbers in `package.json` for releases — merge the
release-please PR instead.

## Version and release flow

1. Conventional commits land on `master`.
2. [Release Please](.github/workflows/release-please.yml) opens/updates a release PR
   with `CHANGELOG.md` and `package.json` version bumps.
3. Merging that PR creates a GitHub Release and git tag (e.g. `v1.2.0`).
4. Android `versionName` / `versionCode` are derived from `package.json` at Gradle build time.

## Code conventions

- **TypeScript** (`capacitor.config.ts`, `src/`): strict mode, match existing style.
- **Java** (`MainActivity.java`): keep native logic minimal; prefer Capacitor config
  for cross-platform settings.
- **Secrets**: never commit keystores, `keystore.properties`, or `google-services.json`.
  See [SECURITY.md](SECURITY.md).

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not file public
issues for security bugs.

## License

By contributing, you agree that your contributions are licensed under the project's
[MIT License](LICENSE).

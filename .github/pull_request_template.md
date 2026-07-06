## Summary

<!-- What changed and why? -->

## Test plan

- [ ] `npm ci && npm run typecheck && npm run audit`
- [ ] `npx cap sync android && cd android && ./gradlew test assembleDebug`
- [ ] Manual: OAuth login, session persistence, external links in Custom Tabs

## Release notes

Uses [Conventional Commits](https://www.conventionalcommits.org/) — release-please will update
[CHANGELOG.md](../CHANGELOG.md) when this lands on `master`.

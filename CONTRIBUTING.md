# Contributing to Zerion Desktop

Thank you for your interest in improving Zerion Desktop. This document covers how
to build, the conventions the codebase follows, and how to submit changes.

## Reporting security issues

**Do not** open a public issue for a security vulnerability. Follow the private
process in [SECURITY.md](SECURITY.md).

## Building

See [BUILDING.md](BUILDING.md). You need JDK 21 and the bundled Gradle wrapper.

## Project layout

- `zerion-desktop-ui`: the Compose desktop UI, wallets, and ZVault.
- `zerion-desktop`: desktop engine wiring (Tor, database, boot).
- `zerion-core*`, `zerion-app*`, `zerion-wire`, `i2p-embedded`: the shared
  messaging engine and Zerion's own wire protocol. The engine modules
  (`zerion-core`, `zerion-app`) carry identity, database, and Tor-integration code
  derived from the Briar/Bramble codebase (GPLv3); see the Attribution section of
  the README.
- `opus-jvm`: the vendored pure-Java Opus codec for voice messages.
- `packaging/flatpak`: the Flatpak manifest and assets.

## Conventions

- **No application logging.** Production code must not log. The `verifyNoLogging`
  gate fails the build if it detects logging calls. Use debug-gated diagnostics
  only where an existing pattern allows it.
- **No swap code.** The wallet ships no asset-swap functionality; the
  `verifyNoSwapCode` gate enforces this.
- **Comments.** Prefer clear code and descriptive commit messages over inline
  comments.
- **Wallet and vault changes** touch fund safety and data-at-rest security. Include
  tests, and describe the security reasoning in the pull request.
- Match the style of the surrounding code.

## Tests and gates

Before submitting, make sure the desktop modules compile and the gates pass:

```
./gradlew :zerion-desktop-ui:compileKotlin \
          :zerion-desktop:compileJava \
          :zerion-desktop-ui:test \
          :zerion-desktop:test \
          :zerion-desktop-ui:verifyNoLogging \
          :zerion-desktop-ui:verifyNoSwapCode \
          --dependency-verification lenient
```

## Pull requests

- Keep changes focused and describe what and why.
- Note any security or fund-safety implications explicitly.
- For anything affecting the wire protocol, keep cross-platform (Android/iOS)
  interoperability in mind.

## License

By contributing, you agree that your contributions are licensed under the
**GNU General Public License v3.0**, the same license as the project.

# Supply chain

Zerion Desktop bundles a small number of third-party binaries and libraries. This
document describes how their integrity is protected and how the build guards
against tampering.

## Bundled Monero binary

The Monero wallet functionality is driven by the official **`monero-wallet-rpc`**
binary, shipped per platform under
`zerion-desktop-ui/appResources/<platform>/monero/`.

- The SHA-256 of each bundled binary is **pinned** in the `verifyMoneroBinaries`
  Gradle task, matched against the official Monero release.
- The gate runs as a dependency of the packaging tasks, so a substituted or
  tampered fund-signing binary cannot be packaged: the build fails if a hash does
  not match.

## Opus / Ogg for voice messages

- The Opus codec is the vendored, pure-Java **Concentus** implementation in the
  `opus-jvm` module. Its provenance is recorded in `opus-jvm/PROVENANCE.md`. No
  native audio code is used.
- The Ogg container library (`vorbis-java`) is pinned by SHA-256 in the
  `verifyOpusDeps` gate.

## Build gates

The build enforces several release gates that fail the build on violation:

- **verifyMoneroBinaries**: bundled Monero binaries match their pinned hashes.
- **verifyOpusDeps**: the Ogg dependency matches its pinned hash.
- **verifyNoLogging**: no application logging in production source.
- **verifyNoSwapCode**: no asset-swap code paths in the wallet.

## Dependency verification

Gradle dependency verification metadata is present under `gradle/`. The desktop
build currently runs with `--dependency-verification lenient` because the
desktop-specific dependencies are not all pinned yet; pinning the full desktop
dependency set is planned. This is documented rather than hidden.

## Reproducibility

The self-contained runtime is produced by `jlink` from the JDK used to build.
Building with a JDK 21 toolchain reproduces the released artifacts. See
[../BUILDING.md](../BUILDING.md).

## Verifying a download

Every release includes a `SHA256SUMS` file covering all artifacts. Verify your
download's hash against it before running. See the README's "Verifying downloads"
section.

# Building Zerion Desktop

These instructions build the application from source on Windows or Linux. They are
written so another developer can reproduce the build without any reference to a
specific machine or local paths.

## Prerequisites (all platforms)

- **JDK 21** (a full JDK, not just a JRE, since the build needs `javac`, `jlink`,
  and `jpackage`).
- The bundled **Gradle wrapper** (`./gradlew` / `gradlew.bat`). Do not install
  Gradle separately; the wrapper pins the correct version.
- Git.

The module set is defined in `settings.gradle`. All modules are pure-JVM; no
Android SDK is required.

### Dependency verification

The build currently runs with `--dependency-verification lenient` because the
desktop-specific dependencies are not yet all pinned in
`gradle/verification-metadata.xml`. Pass that flag on every Gradle invocation
below.

## Build the application image

```
./gradlew :zerion-desktop-ui:createDistributable --dependency-verification lenient
```

The self-contained application image (a bundled Java runtime produced by `jlink`,
the application jars, and the vendored Monero binary) is written to:

```
zerion-desktop-ui/build/compose/binaries/main/app/Zerion
```

Run it with `Zerion/Zerion.exe` (Windows) or `Zerion/bin/Zerion` (Linux).

## Build gates

`createDistributable` and the packaging tasks depend on these gates, which fail
the build if violated:

- **verifyNoLogging**: no application logging in production source.
- **verifyNoSwapCode**: no asset-swap code paths in the wallet.
- **verifyMoneroBinaries**: each bundled `monero-wallet-rpc` matches its pinned
  SHA-256.
- **verifyOpusDeps**: the Ogg container dependency matches its pinned SHA-256.

## Windows packaging

**Requirements**

- JDK 21 (provides `jpackage`).
- **WiX Toolset 3.14** for the MSI. WiX 3.14 requires the **.NET Framework 3.5**
  Windows feature.

Enable .NET 3.5 and install WiX (elevated PowerShell):

```
dism.exe /online /enable-feature /featurename:NetFx3 /all
winget install --id WiXToolset.WiXToolset --version 3.14.1.8722
```

**Build the MSI and the portable ZIP**

```
./gradlew :zerion-desktop-ui:packageMsi --dependency-verification lenient
# then zip the app image from createDistributable for the portable build
```

The MSI is written to `zerion-desktop-ui/build/compose/binaries/main/msi/`.

## Linux packaging (.deb and tar.gz)

**Requirements**

- JDK 21 (`openjdk-21-jdk-headless` or equivalent, which needs `javac`, `jlink`,
  and `jpackage`).
- `fakeroot` and `dpkg-deb` for the `.deb`.

If Gradle cannot find the JDK for its toolchain, register it, for example:

```
echo "org.gradle.java.installations.paths=/usr/lib/jvm/java-21-openjdk-amd64" \
  >> ~/.gradle/gradle.properties
```

**Build**

```
./gradlew :zerion-desktop-ui:createDistributable \
          :zerion-desktop-ui:packageDeb \
          --dependency-verification lenient
```

The `.deb` is written to `zerion-desktop-ui/build/compose/binaries/main/deb/`.
The runtime dependencies are declared in the package (X11, fontconfig, ALSA, and
the standard C/C++ runtimes). Create the `tar.gz` by archiving the app image
directory from `createDistributable`.

## Flatpak

**Requirements**

- `flatpak` and `flatpak-builder`.
- `elfutils` (provides `eu-strip`, used to separate debug info).
- The Flatpak runtime and SDK the manifest pins:

```
flatpak remote-add --if-not-exists --user flathub \
  https://flathub.org/repo/flathub.flatpakrepo
flatpak install --user flathub \
  org.freedesktop.Platform//23.08 \
  org.freedesktop.Sdk//23.08 \
  org.freedesktop.Sdk.Extension.openjdk21//23.08
```

**Build the bundle**

```
cd packaging/flatpak
flatpak-builder --user --force-clean \
  --repo=<repo-dir> <build-dir> chat.zerion.Zerion.yml
flatpak build-bundle <repo-dir> Zerion-1.0.0.flatpak chat.zerion.Zerion
```

The manifest performs a self-contained Gradle build inside the sandbox using the
Flatpak `openjdk21` SDK extension, then packages the resulting application image.
The application id is `chat.zerion.Zerion`.

## Provenance of vendored components

- **Monero.** The `monero-wallet-rpc` binaries under
  `zerion-desktop-ui/appResources/<platform>/monero/` are the official Monero
  release binaries. Their SHA-256 hashes are pinned in the `verifyMoneroBinaries`
  Gradle task and checked at package time, so a substituted or tampered binary
  cannot be shipped. See [docs/SUPPLY_CHAIN.md](docs/SUPPLY_CHAIN.md).
- **Opus / Ogg.** The Opus codec used for voice messages is the vendored pure-Java
  **Concentus** implementation in the `opus-jvm` module (see
  `opus-jvm/PROVENANCE.md`). The Ogg container is the gagravarr `vorbis-java`
  library, pinned by SHA-256 in the `verifyOpusDeps` gate. No native audio code is
  used.

## Reproducibility notes

- Do a clean build (`./gradlew clean`) when switching between Windows and Linux to
  avoid mixing platform-specific build outputs.
- The bundled runtime is produced by `jlink` from the JDK you build with; use a
  JDK 21 build to match the released artifacts.

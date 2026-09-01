# Zerion Desktop Flatpak

Builds the Compose desktop app (`:zerion-desktop-ui:createDistributable`) and
packages it as a Flatpak. Build on Linux (a Linux box, a container, or WSL2 with
a working display for testing).

## Prerequisites
    sudo apt install flatpak flatpak-builder
    flatpak remote-add --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
    flatpak install -y flathub org.freedesktop.Platform//23.08 org.freedesktop.Sdk//23.08 \
        org.freedesktop.Sdk.Extension.openjdk21//23.08

## Build + install (self-hosted, network build)
Run from `packaging/flatpak/`:
    ./build.sh
This runs `flatpak-builder` against `chat.zerion.Zerion.yml` (which points at the
repo checkout two levels up) and installs the result into the `--user` remote.

    flatpak run chat.zerion.Zerion

To export a single-file bundle for distribution instead of installing:
    ./build.sh --bundle --no-install
This writes `Zerion-<version>-<arch>.flatpak`.

## aarch64 (Linux mobile: postmarketOS, Mobian, PureOS, Droidian, FuriOS, ...)
The engine and UI are architecture-independent, the Tor and pluggable-transport
binaries for aarch64 ship in the `org.briarproject:tor-linux` / `lyrebird-linux`
jars, and the aarch64 `monero-wallet-rpc` is bundled under
`appResources/linux-arm64/`. Building the aarch64 Flatpak is therefore just a
matter of targeting that architecture.

**On a native aarch64 host** (or an aarch64 CI runner), install the aarch64
runtime and build:

    flatpak install -y flathub org.freedesktop.Platform//23.08 \
        org.freedesktop.Sdk//23.08 org.freedesktop.Sdk.Extension.openjdk21//23.08
    ./build.sh --arch aarch64 --bundle --no-install

**Cross-building aarch64 from an x86_64 host (e.g. WSL2):** the aarch64 build
commands run under emulation, so install qemu and register binfmt first, then
install the aarch64 runtime for that architecture:

    sudo apt install qemu-user-static binfmt-support
    flatpak install -y flathub \
        org.freedesktop.Platform/aarch64/23.08 \
        org.freedesktop.Sdk/aarch64/23.08 \
        org.freedesktop.Sdk.Extension.openjdk21/aarch64/23.08
    ./build.sh --arch aarch64 --bundle --no-install

Emulated builds are much slower than native ones (the aarch64 JDK runs under
qemu for the jlink/jpackage step). For routine aarch64 releases, a native
aarch64 runner is preferable; the emulated path is for producing a bundle
without dedicated aarch64 hardware.

## Notes
- Provide `icons/256/chat.zerion.Zerion.png` (256x256). A placeholder path is
  referenced by the manifest; drop the real app icon there before building.
- The manifest builds with network access (Gradle fetches deps, and Zerion
  itself needs the network for Tor). A **Flathub** submission requires an offline
  build: vendor the Gradle dependencies (e.g. `flatpak-gradle-generator`) and
  drop the `--share=network` build-arg. Briar's `org.briarproject.Briar` Flathub
  recipe is the reference for that conversion.
- Tor: onionwrapper-java extracts the `tor-linux` binary to the app data dir and
  executes it; `--share=network` + the per-app data dir cover that at runtime.

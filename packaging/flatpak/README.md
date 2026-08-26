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

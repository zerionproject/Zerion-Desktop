#!/bin/sh
set -e
# Build + install the Zerion Flatpak from a Linux host (or WSL2).
cd "$(dirname "$0")"
flatpak-builder --force-clean --user --install \
    build-dir chat.zerion.Zerion.yml
echo "Installed. Run: flatpak run chat.zerion.Zerion"

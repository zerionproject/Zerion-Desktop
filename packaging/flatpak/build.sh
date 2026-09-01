#!/bin/sh
set -e
# Build the Zerion Flatpak from a Linux host (or WSL2).
#
# Usage:
#   ./build.sh [--arch <x86_64|aarch64>] [--bundle] [--no-install]
#
#   --arch      Target architecture. Defaults to the host architecture.
#               aarch64 builds are for Linux mobile devices (postmarketOS,
#               Mobian, PureOS, Droidian, FuriOS, and the Librem 5 / Pinephone
#               class of hardware). Building aarch64 on an x86_64 host needs
#               qemu-user-static + binfmt and the aarch64 runtime installed;
#               see README.md.
#   --bundle    Also export a single-file <out>.flatpak bundle for distribution.
#   --no-install  Do not install into the --user remote (useful with --bundle).
cd "$(dirname "$0")"

ARCH="$(flatpak --default-arch)"
BUNDLE=0
INSTALL=1
while [ $# -gt 0 ]; do
    case "$1" in
        --arch) ARCH="$2"; shift 2 ;;
        --bundle) BUNDLE=1; shift ;;
        --no-install) INSTALL=0; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

APP=chat.zerion.Zerion
MANIFEST="$APP.yml"
REPO=build-repo

BUILD_ARGS="--force-clean --arch=$ARCH --repo=$REPO"
if [ "$INSTALL" -eq 1 ]; then
    BUILD_ARGS="$BUILD_ARGS --user --install"
fi

echo "Building $APP for $ARCH ..."
flatpak-builder $BUILD_ARGS build-dir "$MANIFEST"

if [ "$BUNDLE" -eq 1 ]; then
    VER="$(sed -n 's/.*packageVersion = .\([0-9.]*\).*/\1/p' \
        ../../zerion-desktop-ui/build.gradle | head -1)"
    OUT="Zerion-${VER:-dev}-${ARCH}.flatpak"
    flatpak build-bundle --arch="$ARCH" "$REPO" "$OUT" "$APP"
    echo "Wrote bundle: $(pwd)/$OUT"
fi

echo "Done ($ARCH). Run: flatpak run $APP"

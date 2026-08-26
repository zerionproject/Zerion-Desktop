# Flatpak

Zerion Desktop ships a Flatpak build for Linux. The application id is
`chat.zerion.Zerion`.

## Installing the bundle

From a release `.flatpak` bundle:

```
flatpak install --user Zerion-1.0.0.flatpak
flatpak run chat.zerion.Zerion
```

## Sandbox permissions

The Flatpak requests only what the application needs:

- `--share=network`: Zerion connects over Tor, which needs network access.
- `--socket=wayland` and `--socket=fallback-x11`, `--share=ipc`, `--device=dri`:
  the Compose desktop UI renders through the display server.
- `--socket=pulseaudio`: voice messages and audio calls record and play through
  the audio server.
- `--persist=.`: the account, database, and vault persist under
  `~/.var/app/chat.zerion.Zerion/`.

There is no filesystem-wide access, no camera permission (there is no video
calling), and no device permissions beyond audio and the GPU for rendering.

## Building the Flatpak

See [../BUILDING.md](../BUILDING.md#flatpak). In short, install `flatpak`,
`flatpak-builder`, `elfutils`, and the pinned `org.freedesktop` 23.08 runtime and
SDK plus the `openjdk21` SDK extension, then run `flatpak-builder` with
`packaging/flatpak/chat.zerion.Zerion.yml`. The manifest performs a self-contained
Gradle build inside the sandbox using the Flatpak `openjdk21` SDK.

## Platform security note

The Flatpak build, like other Linux builds, protects data at rest with the
Argon2id password only, with no OS machine-binding as there is on Windows. See
[PLATFORM_SECURITY.md](PLATFORM_SECURITY.md).

## Runtime note

The manifest pins the `org.freedesktop` **23.08** runtime to match the bundled
`openjdk21` SDK extension. This runtime is adequate for the self-hosted bundle;
should Zerion pursue a Flathub listing, the manifest would be updated to a
supported runtime and converted to an offline build with a vendored dependency
set.

# Zerion Desktop

Private, serverless peer-to-peer messaging, an encrypted ZVault, and
non-custodial crypto wallets for Windows and Linux.

Zerion Desktop uses Zerion's own messaging protocols and wire formats. There are
no Zerion servers, no accounts, and no phone number or email registration.
Communication is peer-to-peer and routed through Zerion's Tor-based privacy
architecture.

## What Zerion Desktop is

A single desktop application that combines three things:

- **A messenger** for one-to-one chats, private groups, and channels. It uses
  Zerion's own end-to-end encrypted messaging protocol and wire format, carried
  over Tor with no central Zerion server.
- **A ZVault**, an encrypted local store for notes, passwords, documents, and
  media, protected by a memory-hard password and, on Windows, bound to your
  machine and user account.
- **Non-custodial wallets** for Bitcoin, Ethereum, and Monero, where only you
  hold the keys and all network traffic goes through Tor.

It is a desktop companion to the Zerion mobile apps and speaks the same wire
protocol, so a desktop identity can talk to Android and iOS identities.

## Key features

**Messaging**
- One-to-one end-to-end encrypted chats
- Private groups with admin roles and permissions
- Channels with threaded comments and moderation where shipped
- Replies, reactions, and forwarding
- Disappearing messages with a per-conversation and global default timer
- Offline queue with automatic reconnect

**Attachments**
- Images, documents and PDFs, and video files sent as encrypted attachments
- Voice messages
- Metadata scrubbing on imported images, PDFs, and video containers

**Voice calls**
- Peer-to-peer audio calls over Tor (there is no video calling)
- Incoming and outgoing calls, mute, and microphone and speaker device selection
- Reconnect and clean failure handling

**ZVault**
- Encrypted notes and passwords, documents, and media
- Encrypted backup and restore to a single file
- Change password and re-key with a crash-safe migration
- Isolated crypto wallets, each with its own seed and password

**Wallets**
- **Bitcoin:** native SegWit (BIP84) HD wallet, Electrum over Tor, coin control,
  fee control with Replace-By-Fee, and opt-in Silent Payments (BIP352)
- **Ethereum:** accounts and ERC-20 tokens (web3j), EIP-1559 fees
- **Monero:** the official `monero-wallet-rpc`, view-key scanning on your device,
  daemon connection over Tor, and per-transaction wallet-password authentication
- All wallets are non-custodial and restore from their recovery phrase

## Privacy model

- **Serverless and peer-to-peer.** No Zerion server ever sees your messages,
  contacts, or social graph. You pair by exchanging a link directly.
- **Tor-routed.** All connections go through Tor, and the app fails closed rather
  than falling back to a direct connection.
- **No accounts.** No phone number, email, or username registration.
- **No telemetry or analytics.** The app collects nothing and phones no home.
- **No production logging.** Release builds ship with application logging removed,
  enforced by a build gate.
- **Metadata minimization.** Imported media is scrubbed, temporary files are
  cleaned and randomized, and the app avoids creating a deliberate
  Zerion-specific fingerprint on the network or the chain where it can be avoided.

See [PRIVACY.md](PRIVACY.md) for the full model, including the metadata that the
underlying protocols make unavoidable.

## Security model

- End-to-end encryption with a hybrid post-quantum handshake and message ratchet.
- Encrypted local storage. The messenger database is AES-encrypted and the ZVault
  uses AES-256-GCM with an Argon2id-derived key.
- On Windows, the messenger database key and the ZVault are additionally bound to
  the machine and user with DPAPI, so a copied data directory is not usable
  elsewhere even with the password.
- On Linux, storage is protected by the Argon2id password only, with no OS
  machine-binding yet. This difference is documented honestly in
  [docs/PLATFORM_SECURITY.md](docs/PLATFORM_SECURITY.md).

See [SECURITY.md](SECURITY.md) to report a vulnerability, and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how it fits together.

## Supported platforms

- **Windows** 10 and 11 (x64), via MSI installer or portable ZIP
- **Linux** (x64), via `.deb`, `tar.gz`, or Flatpak

## Installation

Download the artifacts for your platform from the
[Releases](../../releases) page.

- **Windows (MSI):** run `Zerion-1.0.0.msi`.
- **Windows (portable):** unzip `Zerion-1.0.0-windows-x64.zip` and run
  `Zerion/Zerion.exe`.
- **Linux (.deb):** `sudo apt install ./zerion_1.0.0_amd64.deb`.
- **Linux (tar.gz):** extract and run `Zerion/bin/Zerion`.
- **Linux (Flatpak):** `flatpak install --user Zerion-1.0.0.flatpak` then
  `flatpak run chat.zerion.Zerion`.

## Verifying downloads

Every release ships a `SHA256SUMS` file. Verify your download before running it.
On Linux:

```
sha256sum -c SHA256SUMS
```

On Windows PowerShell:

```
Get-FileHash .\Zerion-1.0.0.msi -Algorithm SHA256
```

Then compare the result against `SHA256SUMS`.

## Building from source

See [BUILDING.md](BUILDING.md). In short, you need JDK 21, the bundled Gradle
wrapper, and per-platform packaging tools (WiX on Windows and `flatpak-builder`
for Flatpak). The build verifies the bundled Monero binaries against pinned
checksums and enforces the no-logging and no-swap gates.

## Security considerations

Zerion protects your network identity and your data at rest, but it cannot protect
a compromised device. Malware, a keylogger, or someone with access to your
unlocked machine can read your messages and reach your funds. Keep your machine
and your passwords safe, and treat your wallet recovery phrases as the ultimate
backup.

## Platform differences

Windows binds your data to the machine and user with DPAPI. Linux and Flatpak use
password-only protection today. This is spelled out in
[docs/PLATFORM_SECURITY.md](docs/PLATFORM_SECURITY.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Reporting security issues

Please do not open a public issue for a security vulnerability. Follow the private
disclosure process in [SECURITY.md](SECURITY.md).

## License

Zerion Desktop is free software under the **GNU General Public License v3.0**. See
[LICENSE.txt](LICENSE.txt).

## Attribution

Zerion's messaging protocols and wire formats, including the transport, handshake,
and message ratchet, are Zerion's own work.

The application engine modules (`zerion-core`, `zerion-app`, and related) are a
derivative work of the **Briar / Bramble** codebase
(Copyright 2011 to 2014 Sublime Software Ltd), from which Zerion inherits
identity, contact, message-database, and Tor-integration code, re-homed under the
`org.zerionproject` namespace. As a GPLv3 derivative, that code retains its
original copyright and license. See [LICENSE.txt](LICENSE.txt). Zerion also uses
Briar's `org.briarproject.nullsafety` annotation library.

## Links

- Website: <https://zerion.chat>
- Android: <https://github.com/zerionproject/Zerion>
- iOS: <https://github.com/zerionproject/Zerion-iOS>

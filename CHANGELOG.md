# Changelog

All notable changes to Zerion Desktop are documented here.

## 1.0.1

### Fixed

- **Windows: creating a new profile failed with "Could not store database key
  protection".** On a first-run profile the machine-binding secret was written
  before its key directory existed, so the initial store failed. The directory
  is now created before the secret is written. Existing profiles were unaffected.

## 1.0.0

First public release of Zerion Desktop for Windows and Linux.

### Added

- **Serverless P2P messaging over Tor:** one-to-one end-to-end encrypted chats,
  private groups with admin roles, and channels with threaded comments and
  moderation where shipped. Replies, reactions, forwarding, disappearing messages
  with per-conversation and global default timers, and an offline queue with
  automatic reconnect.
- **Attachments:** images, documents and PDFs, and video files as encrypted
  attachments, plus voice messages, with metadata scrubbing on import.
- **Peer-to-peer audio calls** over Tor, with mute and microphone/speaker device
  selection. Audio only; there is no video calling.
- **ZVault:** an encrypted local store for notes, passwords, documents, and media,
  with encrypted backup and restore, change-password / re-key, and isolated
  per-coin wallets.
- **Non-custodial wallets:**
  - Bitcoin: native SegWit (BIP84) HD wallet, Electrum over Tor, coin control,
    fee control with Replace-By-Fee, and opt-in Silent Payments (BIP352).
  - Ethereum: accounts and ERC-20 tokens with EIP-1559 fees.
  - Monero: the official `monero-wallet-rpc`, on-device view-key scanning, daemon
    over Tor, and per-transaction wallet-password authentication.
- **Windows machine binding:** the messenger database key and the ZVault are bound
  to the machine and user via DPAPI, applied through a crash-safe, verify-before-
  commit migration.
- **Packaging:** Windows MSI and portable ZIP; Linux `.deb`, `tar.gz`, and Flatpak.

### Security and privacy

- No accounts, no telemetry, no analytics, no advertising identifiers, and no
  production application logging (enforced by a build gate).
- Tor fail-closed networking; no direct-connection fallback.
- Encrypted local storage (AES for the messenger database; AES-256-GCM with
  Argon2id for the ZVault).
- Supply-chain gates: bundled Monero binaries and the Ogg dependency are pinned by
  SHA-256; a gate rejects any asset-swap code path.

### Known limitations

- On **Linux and Flatpak**, data-at-rest protection is password-derived (Argon2id)
  only; there is no OS machine/user binding as there is on Windows. Copying the
  data directory to another machine and knowing the password is sufficient to open
  it there. See [docs/PLATFORM_SECURITY.md](docs/PLATFORM_SECURITY.md).
- Bitcoin and Ethereum are public ledgers; the wallet applies practical privacy
  measures but does not make them anonymous the way Monero is.

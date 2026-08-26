# Architecture

Zerion Desktop is a single application that combines a serverless messenger, an
encrypted vault, and non-custodial wallets. This document is a high-level map; the
protocol details live in [`protocol/`](protocol/) and [`wire/`](wire/), and the
overall design in [WHITEPAPER_DESKTOP.md](WHITEPAPER_DESKTOP.md).

## Protocol stack

Zerion Desktop implements the **Zerion messaging protocol stack** directly. The
transport, handshake, and message ratchet (ZTP, ZPP, ZWF, and the Mode 3-Full
ratchet) and the wire formats are Zerion's own. Android, iOS, and Desktop speak
compatible Zerion wire formats for cross-platform interoperability. These are
specified in [`protocol/`](protocol/) and [`wire/`](wire/).

The application engine that carries these protocols, namely identity, the message
database, and Tor integration, is derived from the Briar/Bramble codebase
(GPLv3), re-homed under `org.zerionproject`. Zerion's own protocols replace
Briar's synchronization transport and transport-security protocol on the paths
described in the protocol specs. See the Attribution section of the
[README](../README.md).

## Modules

- **`zerion-core-api` / `zerion-core`**: the messaging engine: identity,
  contacts, database, crypto, and sync.
- **`zerion-core-jvm`**: JVM/desktop bindings for the engine (database config,
  secure random).
- **`zerion-app-api` / `zerion-app`**: the application layer: conversations,
  groups, channels, and voice-call signaling.
- **`zerion-wire`**: the wire-format protocol shared across platforms.
- **`zerion-desktop`**: desktop engine wiring: Tor, the database module, boot, and
  Windows key strengthening.
- **`zerion-desktop-ui`**: the Compose Multiplatform desktop UI, the ZVault, and
  the BTC/ETH/XMR wallets.
- **`opus-jvm`**: a vendored pure-Java Opus codec for voice messages.
- **`i2p-embedded`**: an optional, off-by-default embedded I2P router.

## Networking

All connections are routed through an embedded **Tor** client. Contacts connect to
each other as Tor onion services; there is no central relay or directory. The
application fails closed if Tor is unavailable. See
[TOR_AND_NETWORKING.md](TOR_AND_NETWORKING.md).

## Data at rest

- The messenger database is stored with full-database AES encryption; the database
  key is wrapped with an Argon2id-derived key from your password.
- The **ZVault** stores notes, passwords, documents, media, and all wallet
  material as AES-256-GCM records under an Argon2id-derived master key. See
  [ZVAULT.md](ZVAULT.md).
- On Windows, both the database key and the vault secret are additionally bound to
  the machine and user with DPAPI. See [PLATFORM_SECURITY.md](PLATFORM_SECURITY.md).

## Wallets

The BTC, ETH, and XMR wallets are non-custodial and isolated from each other, each
with its own seed and password inside the vault. All wallet networking is over
Tor. See [WALLETS.md](WALLETS.md).

## Threat model (summary)

Zerion protects your network identity (via Tor, fail-closed), the confidentiality
and integrity of your messages (end-to-end encryption with a post-quantum
handshake), and your data at rest (encrypted storage, and machine binding on
Windows).

It does **not** protect against a compromised device. Malware, a keylogger, or an
attacker with access to your unlocked machine can read messages and reach funds;
while an identity or wallet is unlocked, its keys are in memory. Endpoint security
is the user's responsibility. Public blockchains remain publicly visible for
Bitcoin and Ethereum. See the limitations section of
[WHITEPAPER_DESKTOP.md](WHITEPAPER_DESKTOP.md).

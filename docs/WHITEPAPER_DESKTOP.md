# Zerion Desktop Whitepaper

Version: desktop preview (feature branch). This document describes the design,
security model, and privacy properties of the Zerion desktop application in full:
the secure messenger, the protocols and cryptography it runs, the Tor transport,
voice calls, and the encrypted Vault and crypto Wallet built into it. It is
written to be read before use, so you know exactly what the application does,
what it protects, and what it does not.

Zerion is a privacy-focused, Tor-based secure messenger that uses its own
messaging protocols and cryptography. Its application engine, namely identity, the
message database, and Tor integration, is code derived from the Briar/Bramble
codebase (GPLv3), and we credit Briar's foundational work on Tor-based,
metadata-resistant communication. The messaging protocols, wire formats, and
ratchet layered on top of that engine are Zerion's own. The desktop application
is a peer of the mobile clients: it
speaks the same wire protocols and uses the same message formats, so a desktop
user and a phone user are on the same network. We charge no fees and take no cut
of anything. The project exists for security and privacy, not revenue.

---

## 1. Principles

1. **Tor-mandatory, fail-closed.** Every network connection the application makes
   goes through Tor. If Tor is not ready, the application does not fall back to a
   direct connection. The operation fails instead of leaking your IP address.
2. **No accounts on our servers.** There is no Zerion server that holds your
   identity, your contacts, your messages, or your funds. Your identity is a key
   pair on your device. Your contact address is a Tor hidden-service address, not
   a phone number or an email.
3. **Metadata resistance by design.** The wire protocol uses fixed-size frames, a
   constant sending cadence, and cover traffic, so a network observer cannot tell
   how much you send, when you are typing, or whether you are sending at all.
4. **Local-first.** Keys, message storage, scanning, and signing happen on your
   device. Remote services are used only as a transport or as a view onto a public
   blockchain, never as a place that holds secrets.
5. **No telemetry.** The application does not log to disk in release builds and
   sends no analytics. Diagnostics exist only as an opt-in, in-memory,
   developer-gated facility.
6. **Post-quantum where it counts.** Contact handshakes and the message ratchet
   use hybrid post-quantum cryptography, so a future quantum adversary that
   records traffic today cannot read it later.
7. **Standards-based.** The application builds on published cryptographic
   standards and audited libraries rather than home-grown cryptography.

---

## 2. Architecture

The desktop application is a Compose for Desktop (JVM) program. A user interface
layer drives the application engine. The engine's lower-level plumbing, namely
identity, the encrypted database, the Tor and optional I2P transports, and the
message store, is code derived from the Briar/Bramble core (GPLv3), re-homed
under `org.zerionproject`. Zerion's own wire protocols and ratchet run on top of
that engine. The interface talks to the engine through a set of managers and
receives engine events on the UI thread.

```
Zerion desktop application
├── Identity and accounts   (multi-profile, AES-encrypted database, machine data dir)
├── Messenger
│   ├── Contacts and pairing (zerion:// handshake links, Tor rendezvous)
│   ├── 1:1 messages, groups, channels, attachments, disappearing messages
│   └── Voice calls          (audio, AES-256-GCM over Tor)
├── Transport               (Tor always-on; optional embedded I2P; fail-closed)
├── Protocols               (ZTP, ZWF, ZPP, ZMM) + post-quantum handshake and ratchet
└── Vault                   (Argon2id + AES-256-GCM, machine-bound)
    ├── Passwords, Notes
    └── Wallet              (Ethereum, Bitcoin, Monero)
```

Data is stored per profile under a per-user application directory
(`%APPDATA%\Zerion` on Windows, `~/Library/Application Support/Zerion` on macOS,
the XDG data directory on Linux). Each profile has its own encrypted database, its
own keys, and its own Tor state.

---

## 3. Identity and accounts

- **Your identity is a key pair.** When you create a profile, the application
  generates a long-term signature key pair. Your identity has no phone number and
  no email. Others reach you through a Tor address derived from your keys.
- **Multiple profiles.** You can hold several independent identities on one
  machine. Each profile is a separate account in its own directory, with its own
  encrypted database and Tor state, unlockable with its own password. A profile
  shows only a display-name label before it is unlocked.
- **Password and at-rest encryption.** The profile database is an AES-encrypted
  local database. The database key is held by the account manager and released
  only after you sign in with the correct password. A wrong password is reported
  as a wrong password and never partially unlocks anything. You can change your
  password from Settings.
- **Duress password.** You can set a second password that, when entered at
  sign-in, securely wipes that profile instead of unlocking it. It is stored only
  as a salted hash. The wipe overwrites the profile's files before deleting them.
  This is a best-effort measure and, on solid-state storage, cannot guarantee
  every copy of a block is overwritten.
- **On-disk hardening.** The data directory is restricted to the owner where the
  operating system supports it. On Windows NTFS this owner-restriction is a
  no-op, so treat the account directory as readable by anyone with access to your
  user session.

---

## 4. Contacts and pairing

- **Pairing is by exchanging links.** Your invite is a `zerion://` handshake link
  tied only to your Tor address. To add a contact, you and the other person
  exchange links (out of band, however you like) and each paste the other's link
  with a local alias. There is no directory, no discovery, and no central server
  that learns who talks to whom.
- **Tor rendezvous.** After links are exchanged, the two clients meet over Tor and
  complete the handshake. The interface shows the progress states (waiting for
  connection, offline, connecting, adding contact, failed).
- **The handshake is post-quantum.** Pairing runs a hybrid handshake that combines
  classical and post-quantum key agreement (see Section 8). The link carries a
  commitment to the peer's static key, and the handshake verifies it, so a
  man-in-the-middle who swaps keys is detected. There is no non-post-quantum
  fallback path in the handshake.
- **Per-contact status.** Each contact carries a verified flag and a
  post-quantum flag reflecting the negotiated handshake, both visible in the
  interface.

Contact pairing on desktop is by link exchange, not by QR code. QR codes in the
application are used only to display wallet and donation addresses.

---

## 5. Messaging

The desktop client is a full messenger, a peer of the mobile clients, sharing the
same message formats.

- **1:1 private messages** with delivery and read states (sent, delivered, seen).
- **Group chats** with create, invite, accept, decline, leave, and dissolve, and
  admin roles (a creator plus promote, demote, and remove for members). Group
  images use the same shared encoding as the mobile clients.
- **Channels**, publisher-owned broadcast feeds you create or join by invite link,
  publish text and images to, refresh, leave, or delete.
- **Attachments**, all scrubbed of metadata in memory before sending: images are
  re-encoded and stripped of EXIF, PDFs are scrubbed, and MP4 video is scrubbed
  and sent in a chunked, streaming form. The client negotiates the message format
  with each contact and will not send an attachment type a contact's client is
  too old to understand.
- **Disappearing messages**, a per-conversation auto-delete timer.
- **Quoted replies**, by swiping a message or right-clicking it. The reply
  carries a short quote of the original for context. Any message can also be
  copied to the clipboard.
- **Per-chat lock**, an optional separate password on an individual conversation,
  verified locally.
- **Profile pictures**, scrubbed and re-encoded before they are shared.
- **Notifications** that show only the sender's name, never message content.

---

## 6. Voice calls

The desktop client supports encrypted voice calls over Tor. Calls are audio only
on desktop; an incoming video offer connects as audio. Call media is carried in
fixed 20-millisecond frames and encrypted with **AES-256-GCM** using an ephemeral
key derived per call, over a dedicated Tor stream. The media frame format is
byte-identical to the mobile clients. Calls are off by default: while disabled,
an incoming call is declined automatically without opening the microphone. You
enable them in Settings.

---

## 7. Network and transport

- **Tor is the primary, always-on transport.** The application runs its own Tor
  process and its own Tor transport plugin. All messaging traffic, and the
  wallet's own node and explorer traffic, are routed through the same local Tor
  SOCKS proxy with remote DNS, so hostnames (including `.onion` services) are
  resolved by Tor rather than locally. If the proxy is not available, requests
  fail closed.
- **Fail-closed by construction.** The only transports the desktop registers are
  Tor and, optionally, I2P. There is no LAN, Bluetooth, mesh, or direct-TCP
  transport on desktop, so there is no code path that can send your traffic
  outside Tor or I2P.
- **Optional embedded I2P.** You can enable a second transport that runs an
  embedded I2P router. It is off by default. When enabled, it reseeds over Tor.
  I2P participation has its own network-visibility characteristics and is provided
  for users who want it, not as the default.
- **Offline mode.** A single switch stops all connections.
- **Your own nodes, with health checks.** You choose the node for each wallet
  chain (Ethereum RPC endpoint, Electrum server, Monero node); running your own is
  the strongest option. Each node shows a reachability indicator, probed over Tor,
  so you can tell whether a node is healthy, slow, or unreachable before relying on
  it.
- **Censorship circumvention.** Bridge and pluggable-transport plumbing is present
  for reaching Tor on networks that block it.

---

## 8. Protocols and cryptography

Zerion's wire stack is designed so that a network observer learns as little as
possible: not the content, not the sizes, not the timing, and not whether you are
active. The observer sees a steady stream of identical encrypted frames.

- **ZTP, the transport protocol.** The connection and session layer that runs over
  Tor (and I2P): establishing connections, polling, and managing sessions.
- **ZWF, the wire framing.** Traffic is carried in fixed-size 4096-byte frames, so
  real frames, cover frames, and control frames are indistinguishable to an
  observer without the key. Each frame is authenticated encryption
  (XSalsa20-Poly1305, a 24-byte nonce and a 16-byte authentication tag). A
  process-wide frame counter prevents any nonce from being reused under a key.
- **ZPP, the pull protocol.** Frames are sent on a constant cadence, one frame per
  fixed time slot in each direction, with cover traffic sent when there is nothing
  real to send. This removes the timing and volume signals that reveal when you
  are typing or how much you are communicating.
- **ZMM, the message model.** The record and synchronization codec that rides
  above ZPP and feeds the local encrypted store.

The cryptography underneath:

- **Contact handshake.** A hybrid post-quantum handshake using both **X25519** and
  **ML-KEM-768** for static and ephemeral keys, with a forward-secret key
  encapsulation and a hybrid identity proof. The handshake refuses any
  non-post-quantum path.
- **Message ratchet.** Zerion's ratchet ("Mode 3-Full") combines a
  Diffie-Hellman ratchet with a post-quantum chain: every message carries fresh
  ML-KEM-768 key material, so the session continuously rekeys with post-quantum
  secrets and recovers security after a compromise. Out-of-order messages are
  handled by skipped-key stores.
- **Post-quantum primitives.** ML-KEM-768 for key encapsulation and ML-DSA-65 for
  signatures are available, alongside classical X25519 and Ed25519, with hybrid
  key parsers so a single key can carry both.
- **A note on the identity key.** The transport, the handshake, and the ratchet
  are post-quantum hybrid. The long-term identity (author) signing key is
  classical Ed25519. In practice this means recorded conversations are protected
  against a future quantum adversary by the hybrid handshake and ratchet, while
  the long-term identity signature itself is classical. Each contact's negotiated
  post-quantum status is shown in the interface.

The underlying authenticated-encryption primitive (XSalsa20-Poly1305) and the
classical curves are provided by audited libraries. Zerion's contribution is the
protocol composition above them, not new low-level cryptography.

---

## 9. The Vault

The Vault is a local, file-based encrypted store inside the application that holds
passwords, notes, and the crypto wallet. It is unlocked with its own password,
separate from the profile sign-in password.

- **At rest.** Every item is sealed with its own random key using AES-256-GCM, and
  the item keys are wrapped by a vault master key. The master key is derived with
  **Argon2id** (a memory-hard function, 256 MB) combined with a machine-bound
  secret, through HKDF-SHA256. The password check is a constant-time comparison.
- **Machine binding.** On Windows the machine-bound secret is wrapped with the
  operating system credential store (DPAPI), so a copied Vault cannot be unlocked
  on another machine or by another Windows user even with the password. Where no
  operating system credential store is integrated (currently macOS and Linux), the
  Vault falls back to password-only protection.
- **Hardening.** Unlock enforces a minimum-time floor and a persistent,
  exponentially growing lockout after failed attempts. Locking and a 30-minute
  auto-lock shred the master key from memory. Deleted items are securely
  overwritten. Stored blobs are padded to hide their exact sizes, and secret files
  are named by a keyed hash so the on-disk layout does not reveal what is stored.
- **Passwords and notes.** The Vault stores passwords (with a generator and
  auto-clearing clipboard) and free-form notes. A note can be **locked with its own
  secret key**: it is hidden from the notes list, and its contents are encrypted a
  second time under a key derived from that secret. It is revealed only by typing
  the secret into the notes search, which decrypts the matching hidden notes. This
  is defence in depth over the Vault's own encryption; there is no recovery if the
  note secret is forgotten.

---

## 10. The Wallet

The Wallet lives inside the Vault and shares its protections. It is non-custodial:
you hold your keys, and all of its traffic is routed through the application's Tor.

Wallets are **coin-isolated**. Each wallet is a single coin (Ethereum, Bitcoin, or
Monero) with its own recovery phrase and its own password. A phrase for one wallet
reveals nothing about another. A wallet seed is encrypted first under the wallet
password (Argon2id) and then again under the vault master key, so reading a seed
needs the Vault (password plus machine binding) and the wallet password.

### 10.1 Ethereum

- HD key derivation (BIP32/44), multiple accounts, and fresh receive addresses.
- Native ETH and ERC-20 tokens (built-in USDC, USDT, DAI, plus any custom token
  read from its contract over Tor), with EIP-1559 fee transactions.
- Sends can draw from every funded address in an account, so funds received to
  fresh addresses are spendable without a manual consolidation step.
- Live balances and prices, and full transaction history from a block explorer
  over Tor, with pending, confirmed, and failed status.
- Recipient validation rejects non-Ethereum formats, with an EIP-55 checksum check
  for mixed-case addresses.

### 10.2 Bitcoin

- Native SegWit (BIP84) with `bc1` addresses, fresh receive addresses, and
  automatic change-address rotation, so no address is reused.
- Electrum protocol over Tor for balances, history, and broadcast, with a
  confirmations count from the chain tip.
- Coin control (choose exactly which unspent outputs to spend), batch send (pay
  several recipients in one transaction), and Send Max (sweep with the fee
  deducted from the amount).
- Fee control and Replace-By-Fee: Economy, Normal, and Priority rates read live
  from the server, opt-in RBF signaling (BIP125), and a safe fee-bump that reuses
  the exact original inputs, so the replacement conflicts with the original and a
  double-spend is not possible.
- **Silent Payments (BIP352)**: you can pay a reusable `sp1…` address, and the
  wallet derives a unique, unlinkable output for it. The wallet also derives and
  displays your own reusable `sp1…` receive address. Receiving is an opt-in
  feature: the wallet scans blocks from a chosen start height against a BIP352
  light-client source over Tor and can move received funds with taproot key-path
  signing. The Silent Payments detection and the taproot signer are verified
  against the official BIP352, BIP340, and BIP341 test vectors, and every silent
  send or spend is gated on a known-answer self-test. Before signing a detected
  output, the wallet re-checks it against the Electrum server, so a hostile scan
  source cannot induce a spend of outputs that do not exist. Receiving is off by
  default.

### 10.3 Monero

- Driven by the official `monero-wallet-rpc` binary, so Monero's audited code
  performs all key handling, scanning, and signing. The bundled binary's integrity
  is verified against the official release at build time.
- The daemon connection runs through Tor, so the node never sees your IP address.
- Local scanning with the view key, which stays on your device, so a remote node
  cannot learn which outputs are yours.
- A remote node is treated as untrusted by default, so it cannot bias decoy
  selection or learn key-image queries. Marking a node trusted is an explicit
  opt-in for a node you run yourself.
- Multiple accounts and subaddresses, a restore height for a fast first sync,
  batch sending, address validation, and a wallet-password gate on sends.

### 10.4 Backup and recovery

- Each wallet can be restored from its recovery phrase alone.
- An optional encrypted backup file bundles every wallet (seeds, settings, and the
  address book) into a single file encrypted under a separate passphrase (Argon2id
  plus AES-256-GCM), for off-device storage and restore on another install.
- An address book of saved recipients is stored encrypted inside the Vault.

---

## 11. Threat model and limitations

What the application protects against:

- **Network-level identification.** Tor conceals your IP from the services and
  peers you connect to, and the application fails closed rather than leaking it.
  Fixed-size frames, a constant cadence, and cover traffic hide message sizes,
  timing, and whether you are active.
- **Central metadata collection.** There is no server that holds your contacts,
  your social graph, or your messages. Pairing is by direct link exchange over
  Tor.
- **Harvest-now-decrypt-later.** The hybrid post-quantum handshake and ratchet
  protect recorded conversations against a future quantum adversary.
- **At-rest compromise of a copied Vault.** Argon2id, AES-256-GCM, and (on
  supported systems) machine binding mean a copied Vault is not usable on another
  machine and is infeasible to brute-force with a strong password.
- **On-chain linkage,** to the extent each chain allows: Monero by design, Bitcoin
  through Silent Payments, coin control, and no address reuse.

What it does not, and cannot, protect against:

- **A compromised device.** Malware, a keylogger, or an attacker with your
  unlocked machine can read your messages and reach your funds. While an identity
  or a wallet is unlocked, its keys are in memory. Endpoint security is your
  responsibility.
- **Who your contact really is.** The application verifies that you are talking to
  the holder of the key in the link you pasted, but it cannot know whether that
  person is who you believe them to be. Verify links through a trusted channel.
- **Transparent-chain analysis in general.** Bitcoin and Ethereum are pseudonymous
  public ledgers; the measures here reduce linkage but do not make them anonymous
  the way Monero is.
- **Weak passwords.** Key derivation is strong, but a guessable password
  undermines it.

Current limitations, stated honestly:

- **Desktop transports are Tor and optional I2P only.** The Bluetooth and mesh
  transports that exist elsewhere in the project are not part of the desktop
  build.
- **Calls are audio only** on desktop.
- **Machine binding is Windows-only today.** On macOS and Linux the Vault falls
  back to password-only protection until credential-store integration is added for
  those systems.
- **Owner-restriction of the data directory is a no-op on Windows NTFS**, and the
  duress wipe is best-effort on solid-state storage.
- **Silent Payments receiving** is opt-in and off by default, and stays
  experimental until validated against a live scan source.
- **Monero** runs through the official `monero-wallet-rpc` subprocess; a native
  in-process engine is planned and would not change what Monero protects.

---

## 12. Roadmap

- Credential-store machine binding on macOS and Linux.
- Broader platform packaging for Linux and macOS, including the bundled Monero
  binaries with verified hashes.
- Silent Payments receiving validated against a live scan source, with background
  scanning and a self-hosted scan option.
- A native in-process Monero engine, built reproducibly from official Monero
  release tags and checksum-verified.
- Continued hardening and internal review.

---

## 13. Assurance

- The application builds on audited cryptographic libraries, on published Bitcoin,
  Ethereum, and Monero standards and their reference implementations, and on the
  official Monero wallet code, rather than custom low-level cryptography.
- Security-sensitive code, including the Silent Payments and taproot
  implementations, is checked against official test vectors, and fund-moving paths
  are gated on self-tests.
- Bundled binaries are integrity-checked at build time against their official
  releases.
- The application undergoes internal security review and testing as part of the
  wider Zerion project.

---

## 14. Attribution

Zerion's messaging protocols (ZTP, ZWF, ZPP, ZMM) and its post-quantum handshake
and ratchet are Zerion's own work. The application engine that carries them,
namely identity, the message database, and Tor integration, is code derived from
the
**Briar / Bramble** codebase (© Sublime Software Ltd, GPLv3); the original
copyright and license are retained (see the bundled license), and we credit
Briar's foundational work on Tor-based, metadata-resistant communication. The
wallet relies on open standards (the Bitcoin Improvement Proposals,
Ethereum standards, and the Monero protocol) and on their reference and library
implementations. We are grateful to those communities.

---

*This whitepaper describes the desktop application as implemented at the time of
writing. Features marked as roadmap are not yet available. Nothing here is
financial advice.*

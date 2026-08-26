# Platform security differences

Zerion Desktop's data-at-rest protection is stronger on Windows than on Linux
today. This document states the difference plainly so you can make an informed
decision.

## Common to all platforms

- The messenger database key is never stored in plaintext. It is wrapped with an
  **Argon2id**-derived key from your account password and authenticated, so a
  corrupted or tampered key fails loudly rather than decrypting to garbage.
- The **ZVault** encrypts every item with **AES-256-GCM** under an Argon2id-derived
  master key.
- Argon2id is memory-hard, which makes offline password guessing expensive.

## Windows

- The messenger database key **and** the ZVault master secret are additionally
  bound to the machine and the logged-in user with **DPAPI**
  (`CryptProtectData`, CurrentUser scope).
- This binding is applied to the database key through a **versioned, crash-safe
  migration**: the upgraded key envelope is written and its round-trip is verified
  before the old envelope is removed, and a corrupt or foreign binding fails
  closed. A missing binding secret is never silently regenerated in a way that
  would discard an existing account.
- **Consequence:** copying your data directory to another machine or another
  Windows user and knowing the password is **not** sufficient to open your
  messenger database or vault there. Account backup and recovery remain a separate,
  deliberate mechanism.

## Linux and Flatpak

- Data at rest is protected by the **Argon2id password only**. There is **no** OS
  machine/user binding.
- **Consequence:** on Linux, the account password is the sole factor protecting
  data at rest. Copying the data directory to another machine and knowing the
  password **is** sufficient to open it there.
- The correct mechanism for OS-backed protection on Linux is the freedesktop
  Secret Service (libsecret / gnome-keyring / KWallet). It is **not shipped yet**
  because it must be verified to fail closed across its many failure modes (locked
  or absent keyring, different desktop backends, and the Flatpak sandbox portal)
  before it can be trusted. Until that verification is done, Zerion does **not**
  claim Windows-equivalent machine binding on Linux, and keeps the honest
  password-only protection above. Nothing silently falls back to storing a key in
  plaintext, because the key material is never written in plaintext, only as
  password-wrapped ciphertext.

## Summary

| Protection | Windows | Linux / Flatpak |
|---|---|---|
| Argon2id password stretching | ✅ | ✅ |
| Authenticated encryption at rest | ✅ | ✅ |
| OS machine/user binding (DPAPI) | ✅ | ❌ (not yet) |
| Data dir + password opens it elsewhere | ❌ (blocked) | ✅ (possible) |

On all platforms, keep your password strong and treat wallet recovery phrases as
the ultimate backup.

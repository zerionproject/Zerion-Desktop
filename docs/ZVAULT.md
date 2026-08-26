# ZVault

The ZVault is an encrypted local store inside Zerion Desktop for your notes,
passwords, documents, media, and all crypto-wallet material.

## Encryption

- Every item is encrypted with **AES-256-GCM** (authenticated encryption), so
  tampering or corruption is detected rather than silently accepted.
- Each item's content is encrypted under a fresh, single-use item key, which is
  itself wrapped by the vault master key (envelope encryption with per-role domain
  separation).
- The master key is derived from your vault password with **Argon2id** (a
  memory-hard KDF) combined, on Windows, with a machine-bound secret.
- On Windows, the machine-bound secret is protected with **DPAPI** (bound to your
  Windows user and machine), so a copied vault cannot be opened on another machine
  even with the correct password. On Linux the vault is protected by the Argon2id
  password only. See [PLATFORM_SECURITY.md](PLATFORM_SECURITY.md).

## What it stores

- Encrypted notes and passwords.
- Encrypted documents and media, with metadata scrubbed on import (images
  re-encoded, PDF and supported video containers stripped).
- The seeds, keys, addresses, labels, and settings for the crypto wallets.

## Backup and restore

- You can export the whole vault to a single encrypted backup file, protected by a
  separate passphrase (Argon2id plus AES-256-GCM), for off-device storage.
- Restore brings the vault back on the same or another installation.

## Change password and re-key

Changing the vault password re-keys the vault through a **crash-safe** procedure:
new material is written and verified before the old material is removed, so an
interruption never leaves the vault in a broken or half-migrated state.

## Wallet isolation

Each crypto wallet lives inside the vault with its own seed and its own password.
Wallets are isolated from each other, and spending requires the individual
wallet's password, not just the vault being unlocked.

## Duress

A vault can be configured with a duress password whose verifier is stored as a
salted hash, not the password itself.

## Opening attachments

When you open a document or media item with an external application, the decrypted
bytes are written to a temporary file with a randomized, non-identifying name and
owner-only permissions, and are cleaned up on lock and at startup. Note that
external applications may keep their own copies or recent-file entries outside
Zerion's control.

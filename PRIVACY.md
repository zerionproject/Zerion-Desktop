# Privacy

Zerion Desktop is designed so that using it reveals as little as possible, to us,
to network observers, and to anyone who later gets hold of your device. This
document describes what the application does and does not do, and is honest about
the limits that the underlying protocols impose.

## What Zerion does not collect

- **No Zerion accounts.** There is no sign-up, and no phone number, email, or
  username is required or collected.
- **No analytics.** There is no usage analytics or event tracking of any kind.
- **No telemetry.** The application does not send diagnostic or usage data to
  Zerion or anyone else.
- **No advertising identifiers.** None are read, generated, or transmitted.
- **No installation-tracking identifier.** The application does not create a
  Zerion-specific identifier to count or track installations.
- **No production application logging.** Release builds ship with application
  logging removed, and a build gate fails the release if logging is reintroduced.

There is no Zerion server in the architecture that could collect any of this even
if we wanted it to.

## How your network traffic is handled

- All of the application's connections are routed through the **Tor** network.
- The app **fails closed**: if Tor is not available, it does not fall back to a
  direct connection that would expose your IP address.
- Pairing with a contact is done by exchanging a link directly, over Tor, not
  through any Zerion directory or lookup service.
- Wallet network traffic (Bitcoin via Electrum, Ethereum via RPC, Monero via its
  daemon) also goes through Tor, so those services do not see your IP address.

## Data stored on your device

- The messenger database is encrypted at rest.
- The ZVault (notes, passwords, documents, media, and wallet material) is
  encrypted with AES-256-GCM under an Argon2id-derived key.
- On Windows, the messenger database key and the ZVault are additionally bound to
  the machine and user account via DPAPI. On Linux, protection is
  password-derived only. See [docs/PLATFORM_SECURITY.md](docs/PLATFORM_SECURITY.md).
- Imported images, PDFs, and video containers are scrubbed of embedded metadata,
  and temporary decrypted files (for example, when you open an attachment with an
  external application) use randomized, non-identifying names and are cleaned up.

## Unavoidable protocol metadata

Being honest matters more than sounding absolute. Some metadata is inherent to the
protocols involved and is **not** created by Zerion:

- **Tor.** Using Tor is observable to your local network and to your Tor entry
  point (that you are using Tor, not what you are doing). Zerion does not add a
  distinguishing signature on top of that where it can be avoided.
- **Blockchains.** Bitcoin and Ethereum are public ledgers. Zerion applies
  practical privacy measures (no address reuse, coin control, and opt-in Silent
  Payments for Bitcoin), but these chains do not provide the anonymity that Monero
  does by design. Amounts, addresses, and transaction graphs on Bitcoin and
  Ethereum remain publicly visible.
- **Peers.** A person you talk to necessarily learns that they are talking to you,
  and can keep their own record of the conversation.
- **Opt-in transports.** If you enable an experimental transport such as an
  embedded I2P router, that participation is observable and creates its own
  on-disk identity. These are off by default and clearly marked.

## What we deliberately avoid

Zerion aims for **no Zerion-controlled tracking, no unnecessary identifying
metadata, no deliberate Zerion-specific network or transaction fingerprint where
it can be avoided, Tor fail-closed where required, and maximum practical
unlinkability.** We do not claim, and you should not assume, that any software can
make you "100% anonymous" or "untraceable." What we can and do provide are the
concrete properties described above.

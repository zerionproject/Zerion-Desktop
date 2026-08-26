# Wallets

Zerion Desktop includes non-custodial Bitcoin, Ethereum, and Monero wallets. You
hold the keys; there is no custodian and no Zerion wallet server. All wallet
network traffic goes through Tor.

Each wallet is isolated inside the [ZVault](ZVAULT.md) with its own seed and
password, and every wallet restores from its recovery phrase alone.

## Bitcoin

- Native SegWit **HD wallet (BIP84)**, connecting to an Electrum server over Tor
  with remote DNS.
- **Coin control** (choose which unspent outputs to spend), batch send, and Send
  Max (sweep with the fee deducted).
- **Fee control and Replace-By-Fee:** Economy / Normal / Priority rates read live
  from the server, opt-in RBF signaling (BIP125), and a safe fee-bump that reuses
  the exact original inputs so the replacement conflicts with the original and a
  double-spend is not possible.
- **Silent Payments (BIP352):** you can pay a reusable `sp1…` address, and the
  wallet derives a unique, unlinkable output. Receiving to your own reusable
  address is an **opt-in** feature that is **off by default**: it scans blocks from
  a chosen height against a BIP352 light-client source over Tor, re-checks a
  detected output against the Electrum server before signing, and is gated on a
  known-answer self-test against the official BIP352/BIP340/BIP341 vectors.
- Broadcasts are tracked in a durable journal so an interrupted send is never
  silently lost or double-spent.

## Ethereum

- Accounts and **ERC-20 tokens**, using the web3j library over Tor.
- EIP-1559 fee handling, and sends that show the destination, amount, and fee for
  review.

## Monero

- Driven by the official **`monero-wallet-rpc`** binary, so Monero's own audited
  code performs key handling, scanning, and signing. The bundled binary's
  integrity is verified against the official release at build time (see
  [SUPPLY_CHAIN.md](SUPPLY_CHAIN.md)).
- The daemon connection runs through **Tor**, so the node never sees your IP
  address, and local view-key scanning keeps your outputs private from the node.
- A remote node is **untrusted by default**; marking one trusted is an explicit
  opt-in for a node you run yourself.
- Multiple accounts and subaddresses, a restore height for a fast first sync, and
  address validation.
- **Per-transaction authentication:** every Monero send is prepared without
  broadcasting, reviewed against the exact destination/amount/fee, and then
  authorized with your wallet password. The authorization is single-use and bound
  to that exact transaction; changing anything requires re-preparing, a wrong
  password sends nothing, and a network error is never reported as a successful
  send. The wallet password is used locally and is never sent to the Monero RPC.

## Backup and recovery

- Restore any wallet from its recovery phrase.
- Optionally export all wallets (seeds, settings, and the encrypted address book)
  into a single backup file encrypted under a separate passphrase (Argon2id plus
  AES-256-GCM).

## Privacy notes

Monero is private by design. Bitcoin and Ethereum are public ledgers: the wallet
applies practical measures (no address reuse, coin control, and opt-in Silent
Payments for Bitcoin), but these do not make Bitcoin or Ethereum anonymous the way
Monero is. Amounts, addresses, and transaction graphs on those chains remain
publicly visible.

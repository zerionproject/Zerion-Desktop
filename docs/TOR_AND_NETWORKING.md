# Tor and networking

Zerion Desktop speaks only over the Tor network. There is no Zerion server and no
direct-connection fallback.

## Tor-only, fail-closed

- The application starts an embedded Tor client and routes all traffic through it.
- If Tor is not bootstrapped or a SOCKS proxy is unavailable, network operations
  **fail closed** rather than falling back to a clearnet connection that would
  expose your IP address.
- Contacts reach each other as Tor **v3 onion services**. Your onion service
  private key is generated locally and stored encrypted in the messenger database.

## Pairing

Pairing is done by exchanging a link directly with the other person. There is no
Zerion directory, lookup, or discovery service that sees who you are or who you
talk to.

## Wallet traffic

Wallet network access is also routed through Tor:

- **Bitcoin** connects to an Electrum server over Tor, using remote DNS so name
  resolution does not leak.
- **Ethereum** uses an RPC endpoint over Tor.
- **Monero** connects its daemon through Tor's SOCKS proxy; a remote node never
  sees your IP, and the node is treated as untrusted by default.

The client identifiers presented to these services are neutral, to avoid a
deliberate Zerion-specific fingerprint where it can be avoided.

## Bridges

Where the underlying Tor integration supports it, pluggable-transport bridges
(such as obfs4, snowflake, or meek) can be configured for networks that block
direct Tor access. Bridges are an explicit configuration; the default is a direct
Tor connection, and the app still fails closed rather than leaking.

## Optional I2P

An embedded I2P router is included but is **off by default** and must be
explicitly enabled. It is intended for experimentation. Enabling it starts a
local router that has its own on-disk network identity and whose participation is
observable, so it is a deliberate, informed opt-in. When left disabled, no I2P
files are created. See [ZERION_MESH_AND_I2P.md](ZERION_MESH_AND_I2P.md).

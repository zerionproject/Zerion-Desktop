# Verifying Zerion Desktop releases

Every Zerion Desktop release ships a `SHA256SUMS` file listing the SHA-256 of
each artifact, signed with the Zerion Project OpenPGP key.

- **Key:** `Zerion Project <support@zerion.chat>`
- **Fingerprint:** `8C0B A397 15A4 B25B 4B19  772D 5253 0E65 65A2 5225`
- **Public key:** [`zerion-signing-key.asc`](zerion-signing-key.asc) (also attached to each release)

## Verify a download

1. Import the signing key once:

   ```
   gpg --import zerion-signing-key.asc
   ```

2. From the release, download the file you want plus `SHA256SUMS` and
   `SHA256SUMS.asc`.

3. Check the signature on the checksums:

   ```
   gpg --verify SHA256SUMS.asc SHA256SUMS
   ```

   Look for **`Good signature from "Zerion Project <support@zerion.chat>"`** and
   confirm the fingerprint matches the one above. (A "not certified" warning just
   means you have not marked the key as trusted; the fingerprint is what matters.)

4. Confirm your download's hash is the one that was signed:

   ```
   sha256sum -c SHA256SUMS          # Linux / macOS / Git Bash
   ```

   On Windows PowerShell, compare manually:

   ```
   (Get-FileHash .\Zerion-<ver>.msi -Algorithm SHA256).Hash
   ```

## Windows Authenticode

The `.exe` and `.msi` are not yet Authenticode code-signed (a CA-issued
certificate is planned). Until then, verify them through the GPG-signed
`SHA256SUMS` above.

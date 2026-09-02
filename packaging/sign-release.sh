#!/bin/sh
# Sign a release's SHA256SUMS with the Zerion Project GPG key.
#
# Run from the folder that contains SHA256SUMS (the release staging folder).
# Requires the Zerion Project SECRET key in your GnuPG keyring (created in
# Gpg4win / Kleopatra); gpg will prompt for the passphrase.
#
#   Key: Zerion Project <support@zerion.chat>
#   Fingerprint: 8C0B A397 15A4 B25B 4B19  772D 5253 0E65 65A2 5225
set -e
KEY=52530E6565A25225

if [ ! -f SHA256SUMS ]; then
  echo "No SHA256SUMS in $(pwd) - run this from the release folder." >&2
  exit 1
fi

gpg --local-user "$KEY" --armor --detach-sign --output SHA256SUMS.asc SHA256SUMS
gpg --verify SHA256SUMS.asc SHA256SUMS
echo "Signed -> SHA256SUMS.asc"

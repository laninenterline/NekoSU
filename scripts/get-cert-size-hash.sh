#!/usr/bin/env bash
# Print the DER size and sha256 of a keystore certificate (used as KSU_EXPECTED_* values).
# Usage: scripts/get-cert-size-hash.sh <keystore> <alias> [storepass]
set -euo pipefail
STORE=${1:?keystore path}
ALIAS=${2:?key alias}
PASS=${3:-android}
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
keytool -exportcert -alias "$ALIAS" -keystore "$STORE" -storepass "$PASS" -keypass "$PASS" -file "$TMP" >/dev/null 2>&1
echo "size=$(stat -c%s "$TMP")"
echo "hash=$(sha256sum "$TMP" | cut -d' ' -f1)"

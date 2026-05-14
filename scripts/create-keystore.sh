#!/usr/bin/env bash
# Generates a release keystore and writes keystore.properties
# Run once; keep mmmap-release.jks and keystore.properties backed up and OUT of git.
set -euo pipefail

KEYSTORE_FILE="mmmap-release.jks"
PROPS_FILE="keystore.properties"

if [[ -f "$KEYSTORE_FILE" ]]; then
    echo "$KEYSTORE_FILE already exists — delete it manually to regenerate."
    exit 1
fi

echo "Creating release keystore at $KEYSTORE_FILE"
echo "You will be prompted for a keystore password and key password."

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -alias mmmap \
    -dname "CN=MMMap, O=MMMap, C=GB"

echo
echo "Enter keystore password (same as above):"
read -rs STORE_PASS
echo "Enter key password (same as above, or different):"
read -rs KEY_PASS

cat > "$PROPS_FILE" <<EOF
storeFile=${KEYSTORE_FILE}
storePassword=${STORE_PASS}
keyAlias=mmmap
keyPassword=${KEY_PASS}
EOF

echo
echo "Written to $PROPS_FILE"
echo "IMPORTANT: Add both $KEYSTORE_FILE and $PROPS_FILE to a secure backup."
echo "They are already in .gitignore."

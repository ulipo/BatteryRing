#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE="$ROOT_DIR/batteryring-release.jks"
PROPS="$ROOT_DIR/keystore.properties"
BASE64_FILE="$ROOT_DIR/batteryring-release.jks.base64"
ALIAS="batteryring"

if [[ -e "$KEYSTORE" ]]; then
  echo "ERROR: $KEYSTORE already exists. Refusing to overwrite the permanent signing key." >&2
  exit 1
fi

read -rsp "Keystore password: " STORE_PASSWORD
echo
read -rsp "Key password (press Enter to use the same password): " KEY_PASSWORD
echo
if [[ -z "$KEY_PASSWORD" ]]; then
  KEY_PASSWORD="$STORE_PASSWORD"
fi

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=BatteryRing, OU=BatteryRing, O=BatteryRing, L=Unknown, ST=Unknown, C=IT"

cat > "$PROPS" <<PROPS
storeFile=batteryring-release.jks
storePassword=$STORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$KEY_PASSWORD
PROPS
chmod 600 "$KEYSTORE" "$PROPS"

if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w 0 "$KEYSTORE" > "$BASE64_FILE"
else
  base64 "$KEYSTORE" | tr -d '\n' > "$BASE64_FILE"
fi
chmod 600 "$BASE64_FILE"

echo
echo "Created permanent BatteryRing signing material:"
echo "  $KEYSTORE"
echo "  $PROPS"
echo "  $BASE64_FILE"
echo
echo "GitHub repository secrets to create:"
echo "  BATTERYRING_KEYSTORE_BASE64 = contents of batteryring-release.jks.base64"
echo "  BATTERYRING_STORE_PASSWORD  = the keystore password"
echo "  BATTERYRING_KEY_ALIAS       = $ALIAS"
echo "  BATTERYRING_KEY_PASSWORD    = the key password"
echo
echo "BACK UP batteryring-release.jks securely. Losing it prevents future APK updates signed as the same app."

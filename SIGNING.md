# BatteryRing persistent signing

BatteryRing's GitHub Actions workflow builds both debug and release APKs with the same permanent signing certificate. This makes successive APKs update-compatible, provided the same keystore is kept and the application ID is unchanged.

## One-time setup

From the repository root, run:

```bash
./tools/create-signing-key.sh
```

The script creates three local, ignored files:

- `batteryring-release.jks` — the permanent private signing key.
- `keystore.properties` — local Gradle signing configuration.
- `batteryring-release.jks.base64` — convenient encoded copy for GitHub Secrets.

Back up `batteryring-release.jks` somewhere safe. Do not commit or publish any of these files.

## GitHub secrets

Open the repository on GitHub and go to **Settings → Secrets and variables → Actions → New repository secret**. Create:

- `BATTERYRING_KEYSTORE_BASE64`: complete contents of `batteryring-release.jks.base64`
- `BATTERYRING_STORE_PASSWORD`: keystore password
- `BATTERYRING_KEY_ALIAS`: `batteryring`
- `BATTERYRING_KEY_PASSWORD`: key password

The workflow reconstructs the keystore only inside the temporary GitHub runner, signs both APKs with it, then uploads the APKs as build artifacts.

## Local builds

When `keystore.properties` exists, both local debug and release APKs use the same BatteryRing certificate:

```bash
gradle :app:assembleDebug :app:assembleRelease
```

If `keystore.properties` does not exist, Gradle can still make an ordinary local debug build using the standard Android debug key. CI, however, refuses to build unless the permanent key is configured, so published GitHub artifacts cannot accidentally change signature.

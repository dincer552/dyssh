# DYSSH for Android

A standalone Android SSH client, separated from My App Store.

## Package identity

- Application ID: `com.dincer552.dyssh`
- Release APK asset: `dyssh.apk`
- Distribution: GitHub Releases; My App Store reads this repository from its app catalog.
- Signing: use the same persistent JKS signing identity for every DYSSH update.

## Features

- SSH server profiles
- Password and private-key authentication
- Encrypted private-key and known-host storage using Android Keystore (AES-GCM)
- SSH terminal UI based on Termux terminal-view
- Cloudflare Access SSH through the bundled ARM64 cloudflared binary
- Server status screen
- GitHub Release self-update as a fallback; My App Store can also install/update DYSSH

## Build

GitHub Actions builds the ARM64 cloudflared binary and the SSH PTY bridge, then builds and tests the Android APK.

Configure these repository Actions secrets before expecting a signed release:
- `ANDROID_KEYSTORE_BASE64`: base64 of the existing JKS signing keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore/key password for alias `hattrickssh`.

Do not rotate the JKS signing key for subsequent updates. Android will reject an update signed by a different certificate.

The My App Store catalog expects the latest published release asset to be named exactly `dyssh.apk`.

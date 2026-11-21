# DYSSH for Android

A standalone Android SSH client, distributed through GitHub Releases and My App Store.

## Package identity

- Application ID: `com.dincer552.dyssh`
- Release APK asset: `dyssh.apk`
- Distribution: GitHub Releases; My App Store reads this repository from its app catalog.
- Signing: central HTTPS APK signer using the existing DYSSH JKS identity. Reuse the same JKS and alias for every update.

## Features

- SSH server profiles
- Password and private-key authentication
- Encrypted private-key and known-host storage using Android Keystore (AES-GCM)
- SSH terminal UI based on Termux terminal-view
- Cloudflare Access SSH through the bundled ARM64 cloudflared binary
- Server status screen
- GitHub Release self-update; My App Store can also install/update DYSSH

## Build and release

GitHub Actions builds the ARM64 cloudflared binary and SSH PTY bridge, runs unit tests, builds the release APK, and sends it to the central signing service using a short-lived GitHub OIDC token.

Configure the repository Actions variable `APK_SIGNING_SERVICE_URL` with the central signer's HTTPS URL. Do not add a JKS file or signing password to this repository.

The central signer must have a key registered with:
- Key ID: `dyssh`
- Package: `com.dincer552.dyssh`
- Repository: `dincer552/dyssh`
- JKS alias: `hattrickssh`

The registered JKS must be the original DYSSH signing identity. Never rotate the key: Android will reject updates signed by a different certificate.

Only a verified signed APK is published as the `dyssh.apk` GitHub Release asset.

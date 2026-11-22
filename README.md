# DYSSH for Android

DYSSH is an independent Android SSH client distributed through GitHub Releases and My App Store.

## Application identity

- Android package: `com.dincer552.dyssh`
- Release asset name: `dyssh.apk`
- GitHub repository: `dincer552/dyssh`
- My App Store catalog: `dincer552/socialworst` already references this package, repository and asset name.
- Minimum Android version: Android 8.0 (API 26)
- Native ABI: ARM64-v8a

## Features

- Multiple SSH server profiles with configurable host, port and username
- Password or imported PEM/OpenSSH private-key authentication
- Encrypted profiles, passwords, private keys and known-hosts data using Android Keystore AES-GCM
- SSH host-key verification on first connection and strict known-host checking afterwards
- Interactive terminal based on Termux terminal-view with copy/paste, keyboard input and resize handling
- Optional Cloudflare Access SSH through the bundled ARM64 cloudflared executable
- VM status dashboard for CPU, RAM, disk, uptime and network counters
- GitHub Release self-updater, in addition to My App Store install/update support

## Build and release pipeline

GitHub Actions builds the cloudflared and SSH PTY native components, runs JUnit tests, builds debug and release validation APKs, checks package metadata, confirms both ARM64 native components are packaged, and checks ELF load-segment alignment for 16 KB page-size compatibility.

Pull requests run validation only and upload a debug APK for device testing. They do not call the signing service and do not publish a release.

Only a push to `main` or a manual workflow dispatch can request central signing and publish a production release. The workflow uses a short-lived GitHub OIDC token; no JKS file or signing password is stored in this repository.

### Existing signing identity — do not rotate

The central signer must already contain the original DYSSH signing profile:

- Key ID: `dyssh`
- Android package: `com.dincer552.dyssh`
- GitHub repository binding: `dincer552/dyssh`
- Existing JKS alias: `hattrickssh`

The workflow calls only `/v1/sign`. It deliberately does not call the key-bootstrap endpoint and never creates, replaces or rotates a signing key. If signing returns HTTP 403, read the response body in the Actions log and correct the existing profile's repository/package binding using the authorized signer administration flow. Do not create a new key: Android will reject an update signed with a different certificate.

Before publication, CI runs `apksigner verify`, checks package/version metadata, and compares the signing certificate SHA-256 fingerprint with the previous DYSSH release when one exists. The verified production asset is published as `dyssh.apk`, the exact asset expected by My App Store.

## Updating the app

The in-app updater reads the latest release from this repository and requires the exact `dyssh.apk` asset. Before opening Android's installer, it checks:

- HTTPS release download URL and supported version tag
- Optional GitHub SHA-256 asset digest, when supplied by GitHub
- Downloaded APK package name and versionCode
- Downloaded APK signing certificate against the currently installed DYSSH certificate

The installer hand-off uses a content URI, APK MIME type and read permission grant. Android still displays its normal installation confirmation; apps cannot silently install arbitrary APKs without privileged device-management access or root.

My App Store uses the same release asset and package identity. App updates retain app-private data; do not clear DYSSH app data when updating.

## Device test checklist

Run these checks on a real Android device before considering the app fully accepted:

- [ ] Add a server profile and connect using username/password
- [ ] Import a supported PEM/OpenSSH private key and connect using public-key authentication
- [ ] Confirm the first host fingerprint only after comparing it with the trusted server value
- [ ] Confirm a changed host key is rejected
- [ ] Connect through Cloudflare Access and complete browser authentication
- [ ] Test terminal UTF-8 output, special keys, Ctrl+C, copy/paste, scrolling and resize
- [ ] Disconnect, reconnect, background/foreground the app and reopen a saved profile
- [ ] Check the VM status dashboard and manual refresh
- [ ] Install through My App Store and update an existing DYSSH installation
- [ ] Confirm saved profiles, keys and known-host records remain after update
- [ ] Test the in-app updater and verify Android's package installer opens

Live SSH and Cloudflare Access sessions require a reachable test server and the owner's credentials; CI can validate builds and packaging but cannot claim those external sessions passed without them.

## Development

The GitHub Actions workflow installs Java 17, Go 1.27.3, Android SDK 36, build-tools 36.0.0, and NDK 26.1.10909125. The native build script pins the cloudflared release and applies the checked-in Android patches.

Never commit passwords, private keys, OIDC tokens, signer admin tokens, JKS files or signing passwords.

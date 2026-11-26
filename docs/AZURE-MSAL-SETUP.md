# DYSSH Azure sign-in configuration

DYSSH now uses the Microsoft Authentication Library (MSAL) Android browser authorization-code flow. It no longer uses OAuth device-code polling and does not persist refresh tokens in the app's own preferences.

## Microsoft Entra app registration

Use the existing DYSSH Azure Cost public client registration:

- Application (client) ID: 821c50df-c62c-430e-8691-10d0f005d2b6
- Directory (tenant) ID used by the current Azure profile: 59c6ec38-fc8e-43e1-ac46-f849530b2fed
- Android package: com.dincer552.dyssh
- Redirect URI: msal821c50df-c62c-430e-8691-10d0f005d2b6://auth

In Microsoft Entra admin center → App registrations → DYSSH Azure Coast → Authentication:

1. Add platform Mobile and desktop applications.
2. Add the exact redirect URI above and save.
3. Under Advanced settings, enable Allow public client flows if it is not already enabled.
4. Under API permissions, verify delegated Azure Service Management → user_impersonation is present. Grant consent if the tenant requires administrator consent.

The Android application is deliberately configured with broker_redirect_uri_registered=false; do not add the Android broker signature-hash redirect for this flow. The matching BrowserTabActivity callback is declared in the app manifest.

## Azure authorization

The signed-in identity must exist in the target directory (as a member or guest) and have Cost Management Reader at the subscription scope, or an equivalent role that permits Cost Management Query.

## Security notes

- MSAL maintains its own token cache and performs silent token renewal.
- No client secret is embedded in the APK.
- The app stores account metadata encrypted with Android Keystore; refresh tokens are not copied into that metadata.
- The app requests only the Azure Resource Manager delegated scope needed by the cost query.

## First-device verification

After installing a build containing the MSAL changes:

1. Open Azure Cost and select MICROSOFT İLE BAĞLAN.
2. Complete sign-in in the system browser and return to DYSSH.
3. Confirm the account name appears and both current-month and previous-month cost queries complete.
4. Tap YENİLE to verify silent token acquisition.

If sign-in succeeds but the cost query returns 403, check the Azure Service Management delegated permission and the Cost Management Reader role. A successful sign-in alone does not grant subscription cost access.

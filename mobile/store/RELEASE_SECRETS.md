# Mobile Release Secrets

Configure these as GitHub Actions repository or environment secrets. Never commit the values.

## Android

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` for optional Play internal-track upload

The keystore is decoded to `mobile/android/app/cito-release.jks` only inside the release runner.

## iOS

- `IOS_DISTRIBUTION_CERTIFICATE_BASE64` — App Store distribution `.p12`
- `IOS_CERTIFICATE_PASSWORD`
- `IOS_PROVISIONING_PROFILE_BASE64`
- `IOS_TEAM_ID`
- `IOS_PROVISIONING_PROFILE_NAME`
- `APP_STORE_CONNECT_API_KEY_ID`
- `APP_STORE_CONNECT_ISSUER_ID`
- `APP_STORE_CONNECT_API_PRIVATE_KEY_BASE64`

Use a protected GitHub environment named `mobile-production` with required reviewers for store uploads.

## Review credentials

Store review-account credentials belong in Apple App Store Connect and Google Play Console's restricted app-access fields, not in source control or workflow logs.

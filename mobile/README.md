# Cito Business for iOS and Android

Cito Business is the native merchant application for the Cito platform. It is intentionally separate from the dense Cito Operations web console and focuses on the jobs merchants perform away from a desktop:

- understand today's collections, payouts and transaction status;
- search and inspect payment evidence;
- view payment-channel readiness and settlement context;
- initiate a guarded collection when entitled;
- access Cito Payments, Communications, Identity/Credit/Scoring, Vending, Billing/BaaS and Integrations;
- receive notifications;
- create support and account-deletion requests;
- manage the authenticated session securely.

## Permanent identifiers

| Platform | Identifier |
|---|---|
| Android application ID | `net.citotech.cito.business` |
| iOS bundle identifier | `net.citotech.cito.business` |
| Display name | `Cito Business` |
| URL scheme | `cito-business` |

These identifiers must be registered unchanged in Google Play Console and Apple Developer/App Store Connect before production signing. Renaming them after publication creates the sort of avoidable misery app stores preserve for tradition.

## Reproducible platform generation

The repository keeps the authored Flutter application and generates the standard Android/iOS runner projects with the pinned Flutter SDK. This avoids committing thousands of volatile generated lines while retaining deterministic identifiers and platform settings.

```bash
cd mobile
bash tool/bootstrap_platforms.sh
flutter analyze
flutter test
flutter build appbundle --release \
  --dart-define=CITO_API_BASE_URL=https://cito.coresynergi.es \
  --dart-define=CITO_ENVIRONMENT=production
```

iOS builds require macOS and Xcode 26 or later:

```bash
flutter build ipa --release \
  --dart-define=CITO_API_BASE_URL=https://cito.coresynergi.es \
  --dart-define=CITO_ENVIRONMENT=production
```

## Authentication

The app uses the existing merchant session contract:

- `GET /auth/csrf`
- `POST /auth/authenticateMerchantUser`
- `POST /auth/isMerchantUserLoggedIn`
- `POST /auth/logoutMerchantUser`

Cookies are stored in the operating-system secure store and CSRF tokens are refreshed before mutating requests. Passwords are never persisted.

## Store release

See:

- `store/LAUNCH_CHECKLIST.md`
- `store/PRIVACY_DATA_MAP.md`
- `store/APP_STORE_LISTING.md`
- `store/PLAY_STORE_LISTING.md`
- `.github/workflows/mobile-release.yml`

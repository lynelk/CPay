# Cito Business Mobile Launch Checklist

## Release identity

- [x] Product name: **Cito Business**
- [x] Android application ID: `net.citotech.cito.business`
- [x] iOS bundle identifier: `net.citotech.cito.business`
- [x] URL scheme: `cito-business`
- [x] Initial version: `1.0.0+1`
- [x] Production API: `https://cito.coresynergi.es`
- [x] Android target SDK: API 36
- [x] iOS deployment target: iOS 15.0
- [x] CI iOS runner: macOS 26 / Xcode 26+

## Product acceptance

- [ ] Verify login with a production merchant review account.
- [ ] Verify session restoration after app restart.
- [ ] Verify logout removes the device session.
- [ ] Verify dashboard, transactions, balances, services, notifications and support.
- [ ] Complete a sandbox collection and inspect its canonical transaction timeline.
- [ ] Complete a low-value controlled live collection after formal production approval.
- [ ] Verify account-deletion request creates an authenticated support case.
- [ ] Verify privacy, terms, status and account-deletion web pages are publicly accessible.
- [ ] Confirm no screen displays fabricated balances, provider states or transaction figures.
- [ ] Test low bandwidth, offline startup, request timeout and recovered connectivity.
- [ ] Test supported physical devices, not merely simulators behaving impeccably in their laboratory habitat.

## Apple Developer and App Store Connect

- [ ] Enrol the publishing legal entity in Apple Developer Program.
- [ ] Register `net.citotech.cito.business` as an explicit App ID.
- [ ] Create App Store distribution certificate and provisioning profile.
- [ ] Create the App Store Connect app record.
- [ ] Set privacy policy URL to `https://cito.coresynergi.es/privacy`.
- [ ] Set support URL to `https://cito.coresynergi.es/contact`.
- [ ] Complete App Privacy responses using `PRIVACY_DATA_MAP.md`.
- [ ] Provide an active review merchant account and review notes.
- [ ] Upload 6.9-inch iPhone and 13-inch iPad screenshots, plus any other sizes App Store Connect requests.
- [ ] Confirm encryption declaration. The app uses standard HTTPS/TLS and sets `ITSAppUsesNonExemptEncryption=false`.
- [ ] Upload to TestFlight, complete internal testing, then external/staged testing.
- [ ] Submit version 1.0.0 for review only after production smoke evidence is attached to the release ticket.

## Google Play Console

- [ ] Enrol the publishing legal entity in Google Play Console.
- [ ] Create the app using package `net.citotech.cito.business`.
- [ ] Generate and securely retain the upload keystore.
- [ ] Enable Play App Signing.
- [ ] Set privacy policy URL to `https://cito.coresynergi.es/privacy`.
- [ ] Set account deletion URL to `https://cito.coresynergi.es/account-deletion`.
- [ ] Complete Data Safety using `PRIVACY_DATA_MAP.md`.
- [ ] Complete content rating, app access, ads, target audience and financial-features declarations.
- [ ] Upload phone, 7-inch tablet and 10-inch tablet screenshots.
- [ ] Publish first to Internal testing, then Closed testing, then staged Production rollout.
- [ ] Retain rollback capability and monitor crashes, ANRs, authentication failures and payment errors.

## Security and operations

- [x] Passwords are not persisted.
- [x] Session cookies are stored using operating-system secure storage.
- [x] Mutating API requests use the existing Cito CSRF contract.
- [x] Non-debug builds reject non-HTTPS API base URLs.
- [ ] Configure production crash monitoring before broad release.
- [ ] Configure privacy-respecting product analytics only after consent and legal review.
- [ ] Create mobile incident and rollback runbooks.
- [ ] Assign release owner, security owner, customer-care owner and weekend/on-call coverage.
- [ ] Prepare customer support macros for login, pending payments, failed payments and account deletion.

## Release evidence

Each release must retain:

- source SHA;
- Flutter, Dart, Xcode and Android SDK versions;
- successful `flutter analyze` and `flutter test` output;
- AAB and IPA checksums;
- signing identity fingerprints without private keys;
- TestFlight and Play internal-track version identifiers;
- physical-device certification evidence;
- privacy/data-safety approval;
- production smoke-test results;
- rollback decision and release owner approval.

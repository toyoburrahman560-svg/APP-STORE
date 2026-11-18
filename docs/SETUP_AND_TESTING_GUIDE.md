# APP STORE — Setup, Deployment, Security & Testing Guide

## 1. Architecture Overview
- **Android App (`app/`)**: Kotlin, Jetpack Compose, Material Design 3, MVVM + Repository Pattern, Navigation Compose, Credential Manager Google Sign-In, Cloud Firestore, OkHttp + SHA-256 APK downloader, `FileProvider` PackageInstaller handoff, DataStore preferences, and English + Bengali (`values-bn`) localization.
- **Firebase Backend**:
  - Provisioned Project ID: `gen-lang-client-0675216494`
  - Provisioned Firestore Database ID: `ai-studio-android-appstore-d323b02f-df5e-4be6-bdfa-d2d2aab8b109`
  - Security Rules: `firestore.rules`, `storage.rules`
  - Composite Indexes: `firestore.indexes.json`
  - Cloud Functions (`functions/index.js`): APK validation, VirusTotal malware scan hook, Admin approval/rejection/suspension, atomic download counters, rating aggregation, FCM notifications, audit logging.
- **Separate Web Admin Panel (`admin-web/`)**: Standalone responsive HTML5/CSS3/JS console connecting to the provisioned Firebase project and enforcing admin access via `/admins/{uid}`, custom claims, and verified admin identity.

## 2. Firebase & Google Sign-In Configuration
1. **SHA Fingerprints**:
   Extract debug or release SHA-1 and SHA-256 fingerprints with:
   ```bash
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   Register release signing SHA-1/SHA-256 in **Firebase Console → Project Settings → Android App**.
2. **Optional Services**:
   - Enable **Firebase Cloud Storage** in Firebase Console and deploy `storage.rules`:
     ```bash
     firebase deploy --only storage
     ```
   - Enable **Firebase Cloud Messaging (FCM)** and deploy Cloud Functions:
     ```bash
     cd functions && npm install && cd ..
     firebase secrets:set VIRUSTOTAL_API_KEY
     firebase deploy --only functions,firestore
     ```

## 3. Creating Admin Users & Hosting the Admin Panel
1. Copy your Firebase Web App `apiKey` and `appId` from Firebase Console into `admin-web/app.js`.
2. Add the admin user's UID to `/admins/{uid}` in Firestore (`{ uid, role: "super_admin", createdAt }`) or call `grantAdminClaim` to assign `{ admin: true }` custom claims.
3. Deploy `admin-web/` to Firebase Hosting (`firebase deploy --only hosting`).
4. Inside the Admin Panel → **Categories**, click **Seed All 24 Defaults** to populate all Games and Applications categories in Firestore.

## 4. Build Instructions (APK & AAB)
- **Debug APK**: `gradle :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `gradle :app:assembleRelease` -> `app/build/outputs/apk/release/app-release.apk`
- **Android App Bundle (AAB)**: `gradle :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`

## 5. Testing Checklist
1. Run local Firestore Security Rules unit tests:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
2. Run Android JVM & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. End-to-End Verification Matrix:
   - New & returning Google Sign-In + profile sync in `/users/{uid}`
   - Browse marketplace as guest & authenticated user, search, category filters, sort order
   - Developer registration, APK file inspection (package/version/SDK/SHA-256/signing cert), multi-step submission -> `pending`
   - Admin Panel login, approve/reject/suspend, verify non-admins are blocked
   - Real APK download progress, SHA-256 verification, unknown-sources prompt, PackageInstaller handoff
   - 5-star ratings, reviews, helpful votes, developer replies, and Analytics dashboard

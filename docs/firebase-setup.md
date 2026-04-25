# Firebase Setup Plan

This project is structured so the public GitHub version builds without Firebase.

## Current State

- App code depends on the `Telemetry` interface.
- The checked-in build uses `NoopTelemetry`, which does nothing.
- No Firebase SDK or config file is required for contributors.

## Why This Exists

This keeps the open source repository usable while still preparing the app for store builds with Firebase Crashlytics and Analytics later.

## Planned Production Setup

When you are ready to enable Firebase:

1.  Place your `google-services.json` in the `composeApp/` directory. The build system will automatically detect it and enable the Firebase plugins.
2.  In the Firebase console, enable:
    *   **Authentication** -> **Sign-in method** -> **Anonymous**
    *   **Authentication** -> **Sign-in method** -> **Google**
    *   **Cloud Firestore** -> create a database
3.  Add the app's SHA fingerprints in the Firebase project settings for Android before testing Google Sign-In.
4.  After enabling Google sign-in, download the updated `google-services.json` again and replace the old file. Firebase's Google sign-in flow relies on the OAuth client data from that updated config.
5.  Use a dedicated release keystore for GitHub-distributed builds. Add that keystore's `SHA-1` and `SHA-256` to the Android app in Firebase, then download `google-services.json` again after the fingerprints are saved.
6.  Until the app is published to a Google Play internal testing, closed testing, open testing, or production track, distribute signed APKs through Firebase App Distribution. Android App Bundle uploads require the Firebase project to be linked to a published Google Play app with the same package name.
7.  Use Firestore rules that isolate each user's food diffs under their own UID, for example:

    ```text
    rules_version = '2';
    service cloud.firestore {
      match /databases/{database}/documents {
        match /users/{userId}/foodDiffs/{foodId} {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
      }
    }
    ```

8.  The app sync stores only user-specific food diffs:
    *   custom foods
    *   edits to default foods
    *   deletions of default foods
9.  Keep Firebase config files and keystores out of Git (already added to `.gitignore`).
10.  For GitHub Actions, add the following **Repository Secrets**:
    *   `FIREBASE_TOKEN`: Obtain via `firebase login:ci`.
    *   `FIREBASE_APP_ID`: Your Firebase App ID.
    *   `FIREBASE_TESTERS`: Comma-separated list of tester emails.
    *   `GOOGLE_SERVICES_JSON_BASE64`: Base64 of the latest `google-services.json` downloaded after the Firebase SHA fingerprints were updated.
    *   `ANDROID_RELEASE_KEYSTORE_BASE64`: Base64 of the release keystore file used by GitHub Actions.
    *   `ANDROID_RELEASE_STORE_PASSWORD`: The release keystore password.
    *   `ANDROID_RELEASE_KEY_ALIAS`: The alias of the release signing key inside the keystore.
    *   `ANDROID_RELEASE_KEY_PASSWORD`: The password for that release key.
11.  The GitHub Actions workflow prints the release keystore `SHA-1` during the build. Verify that the printed fingerprint matches the one registered in Firebase whenever Google sign-in is changed or release signing is rotated.

## Why This Setup?

This setup is **Open Source Friendly**:
*   **Privacy**: It keeps your private configuration and tester emails out of the public source code.
*   **Ease of Contribution**: Contributors without Firebase can still build and run the project because the plugins are only applied if the configuration file is found.

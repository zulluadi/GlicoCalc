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
2.  Add a real implementation such as `FirebaseTelemetry` in the platform-specific app code.
3.  Keep Firebase config files out of Git (already added to `.gitignore`).
4.  For GitHub Actions, add the following **Repository Secrets**:
    *   `FIREBASE_TOKEN`: Obtain via `firebase login:ci`.
    *   `FIREBASE_APP_ID`: Your Firebase App ID.
    *   `FIREBASE_TESTERS`: Comma-separated list of tester emails.
    *   `GOOGLE_SERVICES_JSON`: The full content of your `google-services.json` file.

## Why This Setup?

This setup is **Open Source Friendly**:
*   **Privacy**: It keeps your private configuration and tester emails out of the public source code.
*   **Ease of Contribution**: Contributors without Firebase can still build and run the project because the plugins are only applied if the configuration file is found.

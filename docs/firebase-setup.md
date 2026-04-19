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

1. Add a real implementation such as `FirebaseTelemetry` in the platform-specific app code.
2. Keep Firebase config files out of Git:
   - Android: `google-services.json`
   - Apple: `GoogleService-Info.plist`
3. Wire the production build to use `FirebaseTelemetry`.
4. Keep the default GitHub build on `NoopTelemetry`.

## Suggested Next Step

Use build configuration, not separate app modules, to switch between:

- public or local builds without Firebase
- private store builds with Firebase

If you later need separate Firebase projects, add environment-based configurations such as `staging` and `production`.

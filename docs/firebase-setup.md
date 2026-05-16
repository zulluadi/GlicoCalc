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
5.  For iOS Google sign-in:
    *   Add the iOS bundle ID `com.glicocalc.app` in Firebase.
    *   Download `GoogleService-Info.plist` and place it in `iosApp/iosApp/`. This file is ignored by Git.
    *   Copy `REVERSED_CLIENT_ID` from that plist into `GOOGLE_SIGN_IN_REVERSED_CLIENT_ID` in `iosApp/Configuration/Config.xcconfig`.
    *   The current iOS implementation links the Google account with Firebase Auth. Food diff syncing is still implemented on Android only.
6.  Use a dedicated release keystore for GitHub-distributed builds. Add that keystore's `SHA-1` and `SHA-256` to the Android app in Firebase, then download `google-services.json` again after the fingerprints are saved.
7.  Until the app is published to a Google Play internal testing, closed testing, open testing, or production track, distribute signed APKs through Firebase App Distribution. Android App Bundle uploads require the Firebase project to be linked to a published Google Play app with the same package name.
8.  Use Firestore rules that isolate family data to family members and allow invited emails to join, for example:

    ```text
    rules_version = '2';
    service cloud.firestore {
      match /databases/{database}/documents {
        function signedIn() {
          return request.auth != null;
        }

        function family(familyId) {
          return get(/databases/$(database)/documents/families/$(familyId));
        }

        function isMember(familyId) {
          return signedIn()
            && family(familyId).data.members[request.auth.uid] == true;
        }

        function isOwner(familyId) {
          return signedIn()
            && family(familyId).data.ownerUid == request.auth.uid;
        }

        function isInvited(familyId) {
          return signedIn()
            && request.auth.token.email_verified == true
            && family(familyId).data.invitedEmails[request.auth.token.email] == true;
        }

        function isAcceptingInvite(familyId) {
          return isInvited(familyId)
            && request.resource.data.diff(resource.data).affectedKeys()
              .hasOnly(['members', 'memberProfiles', 'updatedAt'])
            && request.resource.data.members[request.auth.uid] == true
            && request.resource.data.members.diff(resource.data.members)
              .affectedKeys().hasOnly([request.auth.uid])
            && request.resource.data.memberProfiles[request.auth.uid].email
              == request.auth.token.email
            && request.resource.data.memberProfiles.diff(resource.data.memberProfiles)
              .affectedKeys().hasOnly([request.auth.uid]);
        }

        function isLeavingFamily(familyId) {
          return isMember(familyId)
            && request.resource.data.diff(resource.data).affectedKeys()
              .hasOnly(['members', 'memberProfiles', 'updatedAt'])
            && !request.resource.data.members.keys().hasAny([request.auth.uid])
            && !request.resource.data.memberProfiles.keys().hasAny([request.auth.uid])
            && request.resource.data.members.diff(resource.data.members)
              .affectedKeys().hasOnly([request.auth.uid])
            && request.resource.data.memberProfiles.diff(resource.data.memberProfiles)
              .affectedKeys().hasOnly([request.auth.uid]);
        }

        match /users/{userId} {
          allow read, write: if signedIn() && request.auth.uid == userId;
        }

        match /families/{familyId} {
          allow create: if signedIn()
            && request.resource.data.ownerUid == request.auth.uid
            && request.resource.data.members[request.auth.uid] == true
            && request.resource.data.memberProfiles[request.auth.uid].email
              == request.auth.token.email;

          allow read: if isMember(familyId) || isInvited(familyId);
          allow update, delete: if isOwner(familyId);
          allow update: if isAcceptingInvite(familyId);
          allow update: if isLeavingFamily(familyId);
        }

        match /families/{familyId}/{document=**} {
          allow read, write: if isMember(familyId);
        }

        match /familyInvites/{inviteId} {
          allow create: if signedIn()
            && isOwner(request.resource.data.familyId)
            && request.resource.data.email == inviteId;

          allow read, update, delete: if signedIn()
            && isOwner(resource.data.familyId);

          allow get, delete: if signedIn()
            && request.auth.token.email_verified == true
            && inviteId == request.auth.token.email;
        }
      }
    }
    ```

9.  The app sync stores family-shared data under `families/{familyId}`:
    *   custom foods
    *   edits to default foods
    *   deletions of default foods
    *   dishes
    *   syncable settings
    Shared custom food and dish deletions are synced as `isDeleted = true` tombstones rather than hard deletes, so a later restore can be propagated across the family.
10. Family managers can name the family, invite members by email, and remove members. The family name is stored on `families/{familyId}.name` and is shown to all members after sync. Invites are in-app allowlist records; no email is sent unless you add a server-side mailer such as a Cloud Function plus an email provider. Invited signed-in users see an in-app join action when the invite matches their verified email. Existing families also show a QR code that encodes the family join payload; Android can scan that QR code when the user wants to join or switch families.
11. Existing users, including family managers, do not move families automatically when invited. They must explicitly join the invited family. If a manager joins another family, the app removes them from the old family and transfers ownership to another remaining member when possible.
12. Non-owner members can leave a family and are moved into their own family sync space. Their UID and profile are removed from the old `families/{familyId}` document, and the remaining family members prune that account from their local member list on the next sync.
13. If invite lookup logs `PERMISSION_DENIED`, verify the deployed Firestore rules include the `familyInvites` read rule above, invites are stored as `familyInvites/{normalizedEmail}`, and the signed-in account has `request.auth.token.email_verified == true`.
14. Keep Firebase config files and keystores out of Git (already added to `.gitignore`).
15. For GitHub Actions, add the following **Repository Secrets**:
    *   `FIREBASE_TOKEN`: Obtain via `firebase login:ci`.
    *   `FIREBASE_APP_ID`: Your Firebase App ID.
    *   `FIREBASE_TESTERS`: Comma-separated list of tester emails.
    *   `GOOGLE_SERVICES_JSON_BASE64`: Base64 of the latest `google-services.json` downloaded after the Firebase SHA fingerprints were updated.
    *   `ANDROID_RELEASE_KEYSTORE_BASE64`: Base64 of the release keystore file used by GitHub Actions.
    *   `ANDROID_RELEASE_STORE_PASSWORD`: The release keystore password.
    *   `ANDROID_RELEASE_KEY_ALIAS`: The alias of the release signing key inside the keystore.
    *   `ANDROID_RELEASE_KEY_PASSWORD`: The password for that release key.
16.  The GitHub Actions workflow prints the release keystore `SHA-1` during the build. Verify that the printed fingerprint matches the one registered in Firebase whenever Google sign-in is changed or release signing is rotated.

## Why This Setup?

This setup is **Open Source Friendly**:
*   **Privacy**: It keeps your private configuration and tester emails out of the public source code.
*   **Ease of Contribution**: Contributors without Firebase can still build and run the project because the plugins are only applied if the configuration file is found.

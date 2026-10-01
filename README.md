# GlicoCalc

GlicoCalc is a Kotlin Multiplatform app that helps children with type 1 diabetes and their caregivers estimate food portions for a chosen carbohydrate target.

The app is designed as a carb-planning aid. It lets users build a meal from individual foods or saved dishes, then calculates the carbohydrate total based on food composition and entered weight.

## Project Status

- Android build is set up and verified.
- Shared Kotlin Multiplatform code is in place.
- An iOS App Store release is planned, but an iOS host app is not yet part of this repository.

## What It Does

- Calculate meal carbohydrates from food weight.
- Calculate required food weight for a target carbohydrate amount.
- Save base foods with carbohydrates per 100 g.
- Save custom dishes made from multiple ingredients.
- Reuse saved foods and dishes during meal planning.

## Important Scope

GlicoCalc is an educational and planning tool only.

- It does not provide medical advice.
- It does not calculate insulin doses.
- It does not replace guidance from a clinician, dietitian, or caregiver protocol.
- Users should verify food data and care decisions independently.

See [DISCLAIMER.md](./DISCLAIMER.md) for the full safety notice.

## Tech Stack

- Kotlin Multiplatform
- JetBrains Compose Multiplatform
- Android target with shared common code
- SQLDelight for local persistence

## Project Structure

- `composeApp/src/commonMain`: shared logic and UI
- `composeApp/src/androidMain`: Android entry point and platform database driver
- `composeApp/src/commonMain/sqldelight`: local database schema

## Getting Started

### Requirements

- JDK 17 or newer
- Android Studio or IntelliJ with Kotlin Multiplatform support
- Android SDK configured locally

### Run On Android

```bash
./gradlew :composeApp:assembleDebug
```

To install from Android Studio, open the project and run the `composeApp` Android configuration on a device or emulator.

### Local Data

The app stores its food and dish data locally using SQLDelight. No Firebase setup is required for the public GitHub build.

## Nightscout Food Export

Open **Settings → Nightscout**, enter your HTTPS site URL and an access token from
Nightscout Admin with `api:food:read`, `api:food:create`, and `api:food:update`
permissions, then select **Export foods**. The URL is saved on this device; the
access token is preserved securely on this device using Android Keystore encryption
or iOS Keychain. Select **Save** to preserve both values across app restarts.
Clearing the token and saving removes it from secure storage.

Exports use the same food names shown in the app, following **Food language**
(including Romanian when `ro` is selected). Each food is exported with a 100 g
portion and its carbs per 100 g, rounded to the nearest whole gram for AAPS
NSClientV3 compatibility. GlicoCalc retains the original decimal carb values. GI bands are exported as Nightscout low (1),
medium (2), or high (3). Unspecified GI leaves any existing Nightscout GI intact. Repeating an export updates the same Nightscout
records, including names after changing the food language. Existing unrelated
Nightscout foods are preserved. Deleted local foods and dishes are not exported,
and local deletions do not delete Nightscout records. Export does not import data
or change the Firebase family sync queue. Category and subcategory are sent as
empty strings for uncategorized foods so AAPS can display them; values assigned
in Nightscout are preserved on subsequent exports.

The export uses the [Nightscout food API](https://github.com/nightscout/cgm-remote-monitor/blob/master/lib/api/food/index.js).

## Open Source Notes

Before using or contributing to this project, read:

- [DISCLAIMER.md](./DISCLAIMER.md)
- [CONTRIBUTING.md](./CONTRIBUTING.md)
- [docs/firebase-setup.md](./docs/firebase-setup.md)

If you publish screenshots or demo data, avoid real personal or health information.

## Roadmap Ideas

- Better onboarding for caregivers
- Portion presets for common foods
- Export and backup options
- Improved validation and testing
- Localization and accessibility improvements

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

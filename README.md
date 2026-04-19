# GlicoCalc

GlicoCalc is a Kotlin Multiplatform app that helps children with type 1 diabetes and their caregivers estimate food portions for a chosen carbohydrate target.

The app is designed as a carb-planning aid. It lets users build a meal from individual foods or saved dishes, then calculates the carbohydrate total based on food composition and entered weight.

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

## Open Source Notes

Before using or contributing to this project, read:

- [DISCLAIMER.md](./DISCLAIMER.md)
- [CONTRIBUTING.md](./CONTRIBUTING.md)

If you publish screenshots or demo data, avoid real personal or health information.

## Roadmap Ideas

- Better onboarding for caregivers
- Portion presets for common foods
- Export and backup options
- Improved validation and testing
- Localization and accessibility improvements

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

# Contributing

Contributions are welcome, but changes should keep the project safe, clear, and narrowly scoped.

## Principles

- Treat the app as a carb-planning aid, not a clinical decision tool.
- Prefer changes that improve clarity, correctness, accessibility, and maintainability.
- Keep medical claims out of UI copy, documentation, and pull requests.
- Avoid adding features that imply diagnosis, dosing, or treatment recommendations.

## Development Setup

1. Install JDK 17 or newer.
2. Install Android Studio and the Android SDK.
3. Open the project root.
4. Run `./gradlew :composeApp:assembleDebug` to verify the Android build.

## Pull Requests

Please keep pull requests focused and include:

- a short explanation of the problem
- the approach taken
- screenshots for UI changes when relevant
- testing notes

## Commit Messages

Use the project commit convention in [.agent/rules/commit-style.md](.agent/rules/commit-style.md). In short, commit messages should use the area prefix style:

```text
<Area>: <Summary>
```

Examples:

- `Calculator: Persist meal draft between app sessions`
- `UI: Keep dish actions visible on small screens`
- `Docs: Document Firebase setup requirements`

## Data And Safety

- Do not commit private user data or health-related records.
- Do not commit local SDK paths, secrets, or generated service configuration unless explicitly intended.
- If you add nutrition data, document the source and any assumptions.

## Feature Direction

Good contribution areas:

- UX improvements for meal entry
- input validation and error handling
- accessibility
- tests
- localization
- documentation

Changes that need extra scrutiny:

- anything affecting carbohydrate calculations
- changes to saved nutrition data structure
- wording that could be interpreted as medical advice

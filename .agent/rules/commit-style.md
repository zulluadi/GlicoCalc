# Commit Style Rule

Use area prefix commit messages in this repository.

## Required Format

```text
<Area>: <Summary>
```

Add a body only when the motivation, validation, migration impact, or safety impact is not obvious from the summary.

## Summary Rules

- Use the narrowest accurate area.
- Keep the summary short, imperative, and sentence case.
- Do not use Conventional Commit prefixes such as `feat:`, `fix:`, or `docs:`.
- Do not stage unrelated user changes.

## Areas

Prefer these app-specific areas:

- `Android`
- `iOS`
- `Shared`
- `UI`
- `Calculator`
- `Foods`
- `Dishes`
- `Meal Types`
- `Database`
- `Sync`
- `Localization`
- `Docs`
- `Build`
- `CI`

Use `App` when a change naturally spans the whole project.

## Examples

```text
Calculator: Persist meal draft between app sessions
UI: Keep dish actions visible on small screens
Docs: Document Firebase setup requirements
Build: Bump Android release version to 0.4
CI: Validate google-services generation
```

## Body Example

```text
Sync: Show sign-in errors from Firebase

- Surface authentication failures in the settings screen.
- Keep local data unchanged when sign-in fails.
```

## Significant Changes

Call out migration, compatibility, or data impact clearly in the body.

```text
Database: Replace saved dish schema

Existing local dish records must be migrated before opening the new build.
```

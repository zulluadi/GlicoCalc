# Commit Workflow

Follow this workflow whenever the user asks to commit changes.

1. Review the worktree with `git status --short`.
2. Identify files that belong to the requested change.
3. Separate distinct changes into individual commits — do not mix unrelated changes in a single commit.
4. Leave unrelated user changes unstaged.
5. Stage only the relevant files for the current commit.
6. Write the commit message using `.agent/rules/commit-style.md`.
7. Include a commit body when the change needs context, validation notes, migration notes, or safety notes.
8. After committing, report the commit hash and mention any remaining unstaged changes.

## Message Selection

Choose the area from the rule file that best matches the change. Prefer a specific area, such as `Calculator`, `Sync`, or `Database`, over a broad area, such as `App`.

Use `Docs` for repository instructions, contribution guidance, README updates, and other documentation-only changes.

## Safety Checks

For this app, be explicit in the commit body when a change affects:

- carbohydrate calculation behavior
- saved nutrition data
- database schema or migrations
- wording that could be interpreted as medical advice
- sync behavior for user-entered data

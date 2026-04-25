# Commit Workflow

Follow this workflow whenever the user asks to commit changes.

1. Review the worktree with `git status --short`.
2. Identify files that belong to the requested change.
3. Leave unrelated user changes unstaged.
4. Stage only the relevant files.
5. Write the commit message using `.agent/rules/commit-style.md`.
6. Include a commit body when the change needs context, validation notes, migration notes, or safety notes.
7. After committing, report the commit hash and mention any remaining unstaged changes.

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

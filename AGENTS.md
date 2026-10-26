# AGENTS.md — krmobdev/antigravity-android

**Owner's mandatory rule: ONE shared development branch, `dev`.**
All agents must read this file before touching this repository.

- Implement, commit, push, and hand off changes **only on `dev`**.
- **Do not** create or develop on individual `feature/*`, `review/*`, `fix/*`, or worktree branches without explicit owner permission. Existing historical branches are read-only.
- `main` and release tags are protected from development work. Never modify them without the owner's explicit approval.
- Verify current branch and `git status` before edits. Preserve existing uncommitted work; do not force-update or reset another agent's checkout.
- Each handoff must include the current `dev` commit SHA and validation status.

The rest of the Android runtime, environment, safety, and documentation rules are in [config/AGENTS.md](config/AGENTS.md), which is also propagated to the app runtime and core seed. Follow **all** of those rules.

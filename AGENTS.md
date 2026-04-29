# AGENTS.md

This repository also keeps Claude-oriented instructions in `CLAUDE.md` and
`.claude/`. Codex should treat those files as project guidance.

## Project Instructions

- Read `CLAUDE.md` for the project overview, architecture, coding rules, and
  required verification steps.
- When changing code, follow `.claude/skills/implementation-workflow/SKILL.md`
  as the project workflow, adapted to Codex's normal approval and execution
  rules.
- When creating or updating tests, follow
  `.claude/skills/unit-test/SKILL.md`.
- When committing changes, follow `.claude/skills/git-commit/SKILL.md`.
- For reviews, use `.claude/agents/code-review.agent.md` as the review rubric.

## Codex Notes

- Claude custom agents are not directly callable as Codex custom sub-agents in
  this environment. Use the agent files as instructions or review rubrics.
- The Claude skills in `.claude/skills/` are compatible with Codex skill
  layout and can be symlinked into `$CODEX_HOME/skills` for discovery in future
  Codex sessions.

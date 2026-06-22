# AGENTS.md

AI coding agent instructions for the **python-embed** project.

## Obsidian Memory

This project uses an Obsidian vault (`agent-memory`) for cross-session memory.
On session start, read the project context and preferences:

```bash
obsidian vault="agent-memory" read path="Projects/python-embed.md"
obsidian vault="agent-memory" read path="Preferences/general.md"
obsidian vault="agent-memory" read path="Preferences/coding-style.md"
obsidian vault="agent-memory" read path="Preferences/workflow.md"
```

### During Session

- All documentation must be managed within Obsidian. Do not leave any documentation within the project.

- **Decision made** → create `Decisions/{title}.md`
- **Task completed/created** → update `Tasks/active.md`

### Session End

- Update `Tasks/active.md` and `Decisions/{title}.md`
- Rebuild `_Index.md` if many notes were added

## Git

- Never add `Co-authored-by` trailer to commits.

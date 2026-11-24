# Documentation Guidelines for Ezkey Project

## Language Policy

**English is the official language of the Ezkey repository.**

This policy applies to everything written for the repository and its contributors:
- README files and the documentation corpus (`docs/`, `product-docs/`, module docs)
- Methodology artifacts (`I-*`, `TB-*`, visions, briefs, walk gates, hygiene lanes)
- Agent guides, rules and skills (`AGENTS.md`, `CLAUDE.md`, `.claude/`, `.cursor/`)
- GitHub issues, pull request titles, descriptions and review comments
- Commit messages
- Source code identifiers and code comments, in every language
- Any other Markdown file (`.md`) in the project

It applies whatever language the author speaks or dictates in: a maintainer dictating in French
still produces English issues, PRs and docs.

### What this policy does not cover

- **Product localization:** user-facing strings shipped in French (Admin UI and mobile `fr`
  locales, the French mirror of the public site and monthly digest) are product content, not
  repository documentation.
- **Existing French content:** documents, issues and PRs already written in French are accepted as
  they are. Do not translate or rewrite them just to apply this policy; switch to English when the
  content is next substantially reworked.

### Rationale

- **Global Accessibility**: English ensures the widest possible audience can understand and contribute to the project
- **Consistency**: A single language prevents fragmentation and confusion across documentation
- **Open Source Standards**: English is the de facto standard for international open source projects
- **Developer Collaboration**: Facilitates collaboration among contributors from different countries

### Enforcement

- Reviewers check that new or modified content in scope is in English
- Agents (Claude Code, Cursor) find this rule through the root [`AGENTS.md`](../AGENTS.md)

### Exceptions

An exception requires the maintainer's explicit go for a given item, recorded where the item lives
(for example in the PR description).

### Getting Help

If you need assistance translating documentation to English:
- Use translation tools as a starting point
- Ask other contributors for review
- Request help in the pull request comments

## Other Documentation Standards

### Formatting

- Use proper Markdown syntax
- Follow the project conventions in [`AGENTS.md`](../AGENTS.md) and the module `AGENTS.md` files
- Use UTF-8 encoding without BOM
- Use LF (Unix) line endings

### Structure

- Include a clear title and introduction
- Use appropriate heading hierarchy (H1, H2, H3, etc.)
- Provide examples where applicable
- Keep documentation up-to-date with code changes

### Best Practices

- Write clear, concise, and accurate documentation
- Use proper grammar and spelling
- Include code examples when relevant
- Link to related documentation
- Update documentation as part of feature development, not as an afterthought

---

**Remember: English is the language of the Ezkey repository.**

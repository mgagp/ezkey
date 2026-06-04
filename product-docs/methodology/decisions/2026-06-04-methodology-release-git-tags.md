---
public: true
---
# Methodology release Git tags

## Date

2026-06-04

## Context

The methodology product now has independent semantic versioning and a first published release,
`1.0.0`. The version file and release note make the public snapshot visible to readers, but the
source repository also needs a stable Git reference so future release notes can be drafted from a
clear baseline.

Without a repository anchor, the next publication would have to infer "changes since 1.0.0" from
dates or file history, which is less precise and more fragile than comparing against an explicit Git
ref.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Long-lived release branch** | Familiar release-management pattern; can carry maintenance work | Implies an ongoing maintenance line and branch policy the methodology does not currently need |
| **B. Lightweight Git tag** | Minimal ceremony; clear commit anchor | Lacks release metadata and message |
| **C. Annotated Git tag** | Clear immutable release anchor with release metadata; easy baseline for future diffs | Requires one small publication step |

## Decision

Use an **annotated Git tag** for each published methodology release.

Tag naming convention:

```text
methodology/v<semver>
```

Example:

```text
methodology/v1.0.0
```

The tag must point to the commit that contains the matching methodology version file, release note,
and related publication changes.

Do not create a long-lived methodology release branch unless the methodology later needs maintained
release lines. The current publication model is snapshot-based, so a tag is the proportional
reference point.

## Consequences

- Future release-note drafting should compare from the latest `methodology/v*` tag.
- The `methodology-release` skill should look for the previous methodology tag before falling back
  to the `released` date.
- Publishing a methodology version now includes creating and pushing the annotated tag alongside the
  release commit.
- Ezkey software releases remain separate; this tag namespace is only for the methodology product.

## Initial anchor

The first methodology release is anchored as:

```text
methodology/v1.0.0
```

This tag corresponds to the first formally versioned methodology publication.

## Related documents

- [`../methodology-publication-and-versioning.md`](../methodology-publication-and-versioning.md)
- [`2026-06-02-methodology-semantic-versioning-and-release-notes.md`](2026-06-02-methodology-semantic-versioning-and-release-notes.md)
- [`../release-notes/2026-06-02-1.0.0.md`](../release-notes/2026-06-02-1.0.0.md)

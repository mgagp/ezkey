<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Handoff — Auth API error attribution (superseded presentation rule)

**Status:** presentation rule **superseded** by [#1052](https://github.com/mgagp/ezkey/issues/1052)
docs close-out (2026-10-27). Keep this file as historical briefing context for the mobile
implementation slice; do **not** follow the old “show Auth `detail`” guidance.

## Canonical rule (current)

Authenticator clients **MUST**:

1. Branch on HTTP **status** and RFC 9457 problem **`type`**.
2. **Never display** `detail` or `title` verbatim to end users — map `type` to local / localized
   copy.
3. Treat non-Ezkey Problem Details (edge/WAF JSON, HTML, transport failures) as generic
   unreachable / request-failed copy.

Canon:

- [`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../docs/CLIENT_BEHAVIOUR_CONTRACT.md) §7
- [`docs/MOBILE_DEVELOPER_GUIDE.md`](../../docs/MOBILE_DEVELOPER_GUIDE.md) § Error Handling Expectations
  (full 19-type table)
- [`MOBILE_API_MAPPINGS.md`](MOBILE_API_MAPPINGS.md) notes

The Dart SDK doc comment on `EzkeyProblem.detail` already says not to display it to end users
(documentation-only alignment; no SDK code change in the docs PR).

## What this handoff originally asked (historical)

Origin Auth errors use `type` under `https://ezkey.io/problems/`. Edge/WAF responses before origin
are not Auth API. An earlier draft proposed allowlisting the Ezkey namespace and **showing** catalog
`detail` for those types. **That display rule is withdrawn** — map `type` instead; never show
`detail`.

Implementation work that still may be useful (code, not docs):

- Single helper that classifies Ezkey vs non-Ezkey `type` and returns **localized** copy (never raw
  `detail`).
- Wire enrollment / pending / detail error extractors to that helper.
- Unit tests: Ezkey type → local string; edge/WAF fixture → generic fallback.

Do not invent `I-*` / `TB-*` unless the operator asks after the slice.

# Ezkey Mobile — Google Play publishing checklist

Short checklist for when you target a **production** Play listing. It is not a substitute for legal review.

## Developer account

Prefer a **Google Play organization** account (DUNS, verified address, public organization name) when a legal entity exists or can be created quickly. A personal account is cheaper up front ($25 one-time) but migrating to an organization later is not trivial on Google's side. Organization accounts also match Ezkey's self-hosted / enterprise-facing posture.

## Release tracks

Use the same app, stacked tracks:

1. **Internal testing** — up to 100 email testers, minutes to propagate, no Play review. Validate the signed AAB and critical flows first.
2. **Closed testing** — email lists or Google Groups; light Play review. New developer accounts must have **12 testers active for 14 days** before production.
3. **Production** — public listing.

Recommended path: **Internal → Closed → Production**. Open testing (public early access) is optional.

Delivery is **AAB** (not APK). Play App Signing holds the app-signing key; you keep the **upload key** — see [`MOBILE_RELEASE_SIGNING.md`](MOBILE_RELEASE_SIGNING.md).

## Store listing

- [ ] **App name** and **short description** match the product (e.g. Ezkey Authenticator).
- [ ] **Screenshots** and **feature graphic** for phone (and tablet if required).
- [ ] **Privacy policy URL** (publicly reachable HTTPS). Canonical page: `https://ezkey.org/privacy.html` (`sites/ezkey-org/privacy.html`; French: `/fr/confidentialite.html`). Still paste this URL into Play Console at submission.
- [ ] **Support contact** (email or site) consistent with the listing.

## Privacy and data

- [ ] **Data safety** form in Play Console: align answers with what the app actually stores (enrollments, keys, API usage) and transmits.
- [ ] No collection beyond what you declare; **no surprise** telemetry without disclosure.

## Technical

- [ ] **Release signing** configured (upload key / app signing by Google Play as you prefer).
- [ ] **Version code** / **version name** bumped per release.
- [ ] **Target API level** meets Play requirements for the release date.
- [ ] **Open source notices**: run `yarn license:app-data` after dependency changes so in-app licenses stay accurate.

## Optional polish

- [ ] **Monochrome adaptive icon** (Android 13+) — see `mipmap-anydpi-v26/ic_launcher*.xml`.
- [ ] **Back gesture** and predictive back behavior on supported devices.

## Notes

- This app follows a **pull-based** model for pending authentication; do not imply background tracking in the listing unless you add it and disclose it.

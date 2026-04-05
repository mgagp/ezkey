# Ezkey Mobile — Google Play publishing checklist

Short checklist for when you target a **production** Play listing. It is not a substitute for legal review.

## Store listing

- [ ] **App name** and **short description** match the product (e.g. Ezkey Authenticator).
- [ ] **Screenshots** and **feature graphic** for phone (and tablet if required).
- [ ] **Privacy policy URL** (publicly reachable HTTPS).
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

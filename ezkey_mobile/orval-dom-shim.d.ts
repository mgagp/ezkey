/**
 * Orval 8.25+ emits `HeadersInit` in generated fetch helpers.
 * React Native's TypeScript config does not include the DOM lib, so the
 * generated client would otherwise fail `tsc` on that name.
 *
 * Runtime: Hermes/RN already provide `Headers`. This declaration only
 * covers the Fetch `HeadersInit` union used by Orval's `getHeaders` helper.
 */
type HeadersInit = Headers | Record<string, string> | string[][] | [string, string][];

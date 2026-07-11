/**
 * IP whitelist validation for API key IP/CIDR configuration.
 *
 * Validates that each entry is either a valid IPv4 or IPv6 address, or a valid
 * CIDR range (e.g. 192.168.1.0/24, 2001:db8::/32). Aligned with backend
 * ApiKeyService validation (IPAddressString from ipaddress library).
 *
 * Used by Create API Key and Edit API Key forms to provide immediate
 * client-side feedback and avoid 400 errors on submit.
 */

// IPv4: four octets 0-255
const IPV4_OCTET = '(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])';
const IPV4_REGEX = new RegExp(`^(${IPV4_OCTET}\\.){3}${IPV4_OCTET}$`);

// IPv4 CIDR: IPv4 + /0-32
const IPV4_CIDR_REGEX = new RegExp(
  `^(${IPV4_OCTET}\\.){3}${IPV4_OCTET}/([0-9]|[1-2][0-9]|3[0-2])$`,
);

// IPv6: common forms including :: compression (simplified but covers standard notation)
const IPV6_REGEX =
  /^(?:(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,7}:|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}|:(?::[0-9a-fA-F]{1,4}){1,7}|::)$/;

// IPv6 CIDR: IPv6-like (contains :) + /0-128; broad pattern to cover :: and full notation
const IPV6_CIDR_PREFIX = '(?:[0-9a-fA-F]{1,4}:){0,7}[0-9a-fA-F]{0,4}|::(?:[0-9a-fA-F]{1,4}:){0,7}|(?:[0-9a-fA-F]{1,4}:){1,8}:?';
const IPV6_CIDR_REGEX = new RegExp(
  `^(${IPV6_CIDR_PREFIX})/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$`,
);

/**
 * Checks if a single string is a valid IP address (IPv4 or IPv6) or CIDR range.
 */
function isValidIpOrCidr(entry: string): boolean {
  const trimmed = entry.trim();
  if (!trimmed) return false;
  return (
    IPV4_REGEX.test(trimmed) ||
    IPV4_CIDR_REGEX.test(trimmed) ||
    IPV6_REGEX.test(trimmed) ||
    IPV6_CIDR_REGEX.test(trimmed)
  );
}

export type IpWhitelistParseResult =
  | { success: true; entries: string[] }
  | { success: false; message: string; lineNumber: number; value: string };

/**
 * Parses a textarea value (newline-separated) into trimmed non-empty lines
 * and validates each line. Returns either the list of valid entries or
 * an error with the first invalid line number and value.
 *
 * @param textareaValue - Raw string from the IP whitelist textarea
 * @returns Parse result with entries array or error details
 */
export function parseAndValidateIpWhitelist(
  textareaValue: string | undefined | null,
): IpWhitelistParseResult {
  const lines = (textareaValue ?? '')
    .split('\n')
    .flatMap((s) => {
      const trimmed = s.trim();
      return trimmed ? [trimmed] : [];
    });

  if (lines.length === 0) {
    return { success: true, entries: [] };
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!isValidIpOrCidr(line)) {
      return {
        success: false,
        message: `Invalid IP or CIDR on line ${i + 1}: "${line}"`,
        lineNumber: i + 1,
        value: line,
      };
    }
  }

  return { success: true, entries: lines };
}

/**
 * Returns an array of trimmed, non-empty lines from the textarea value.
 * Does not validate; use parseAndValidateIpWhitelist when validation is needed.
 */
export function parseIpWhitelistLines(
  textareaValue: string | undefined | null,
): string[] {
  return (textareaValue ?? '')
    .split('\n')
    .flatMap((s) => {
      const trimmed = s.trim();
      return trimmed ? [trimmed] : [];
    });
}

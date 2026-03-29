/**
 * Normalizes user input to the API format: eight groups of four digits.
 * Accepts pasted digits with or without dashes.
 */
export function normalizeRecoveryCodeInput(raw: string): string {
  const digits = raw.replace(/\D/g, '');
  if (digits.length !== 32) {
    return raw.trim();
  }
  const parts: string[] = [];
  for (let i = 0; i < 8; i++) {
    parts.push(digits.slice(i * 4, i * 4 + 4));
  }
  return parts.join('-');
}

const RECOVERY_CODE_PATTERN =
  /^\d{4}-\d{4}-\d{4}-\d{4}-\d{4}-\d{4}-\d{4}-\d{4}$/;

export function isValidRecoveryCodeFormat(code: string): boolean {
  return RECOVERY_CODE_PATTERN.test(code.trim());
}

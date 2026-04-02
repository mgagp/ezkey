const E164_PATTERN = /^\+[1-9][0-9]{7,14}$/;

export function normalizePhoneNumberInput(value: string | null | undefined): string | undefined {
  if (value == null) return undefined;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;

  let normalized = '';
  for (const ch of trimmed) {
    if (/\d/.test(ch)) {
      normalized += ch;
      continue;
    }
    if (ch === '+' && normalized === '') {
      normalized += ch;
      continue;
    }
    if (/\s/.test(ch) || ch === '-' || ch === '(' || ch === ')' || ch === '.') {
      continue;
    }
    throw new Error('invalid-phone-number');
  }

  if (!E164_PATTERN.test(normalized)) {
    throw new Error('invalid-phone-number');
  }

  return normalized;
}

export function isPhoneNumberInputValid(value: string | null | undefined): boolean {
  try {
    normalizePhoneNumberInput(value);
    return true;
  } catch {
    return false;
  }
}

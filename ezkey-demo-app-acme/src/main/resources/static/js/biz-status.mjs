/**
 * DOM-safe status helpers for the ACME demo dashboard business-approval widget.
 *
 * Caller-controlled values (approverIdentifier, server messages, challenge text)
 * must never be interpolated into innerHTML — use textContent / createElement.
 *
 * Decision: code-scanning triage Lot B (alert #1 self-XSS), approved Marc 2026-10-13 19:40.
 */

/**
 * Formats a server-provided challenge for display (minimum two digits, typical Ezkey demo).
 *
 * @param {unknown} value
 * @returns {string}
 */
export function formatChallengeForDisplay(value) {
  if (value === null || value === undefined) return '';
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return String(value);
  const s = String(Math.trunc(n));
  return s.length < 2 ? s.padStart(2, '0') : s;
}

/**
 * Renders an inline status row with icon + text without HTML injection.
 *
 * @param {ParentNode & { className: string, classList: DOMTokenList }} el
 * @param {string} cssClass
 * @param {string} icon
 * @param {string} text
 * @param {{ createElement: (tag: string) => HTMLElement }} documentRef
 */
export function showRowStatusOn(el, cssClass, icon, text, documentRef) {
  if (!el) return;
  el.className = 'biz-row-status ' + cssClass;
  el.replaceChildren();
  const iconSpan = documentRef.createElement('span');
  iconSpan.className = 'biz-st-icon';
  iconSpan.textContent = icon == null ? '' : String(icon);
  const textSpan = documentRef.createElement('span');
  textSpan.textContent = text == null ? '' : String(text);
  el.append(iconSpan, textSpan);
  el.classList.remove('hidden');
}

/**
 * Renders the numeric-challenge strip with textContent for the code value.
 *
 * @param {ParentNode & { classList: DOMTokenList }} el
 * @param {unknown} challengeValue
 * @param {{ createElement: (tag: string) => HTMLElement, createTextNode: (data: string) => Text }} documentRef
 */
export function showChallengeStripOn(el, challengeValue, documentRef) {
  if (!el) return;
  const code = formatChallengeForDisplay(challengeValue);
  el.replaceChildren();
  el.append(
    documentRef.createTextNode(
      "A numeric challenge is required on the approver's device. Enter code "
    )
  );
  const codeSpan = documentRef.createElement('span');
  codeSpan.className = 'biz-challenge-code';
  codeSpan.textContent = code;
  el.append(codeSpan);
  el.append(documentRef.createTextNode(', then approve.'));
  el.classList.remove('hidden');
}

/**
 * Clears a challenge strip.
 *
 * @param {ParentNode & { classList: DOMTokenList } | null} el
 */
export function hideChallengeStripOn(el) {
  if (!el) return;
  el.classList.add('hidden');
  el.replaceChildren();
}

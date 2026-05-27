/*
 * Glossary decoration & tooltips.
 *
 * - decorateGlossaryIn(rootEl, glossary): walks text nodes inside `rootEl`,
 *   wraps the first occurrence of each recognized token/term in a
 *   <span class="glossary-term" data-term-key="…">…</span> per text node.
 * - setupGlossaryTooltips(glossary): installs a singleton tooltip element
 *   and delegates hover/focus on .glossary-term to show it.
 *
 * Decoration is conservative: it skips text inside <a>, <code>, <pre>,
 * <h1>..<h4>, <figure.diagram-rendered>, and anything already inside
 * a .glossary-term span.
 */

const SKIP_ANCESTORS = new Set([
  'A', 'CODE', 'PRE', 'H1', 'H2', 'H3', 'H4', 'FIGCAPTION',
]);

let tooltipEl = null;
let glossaryCache = null;

export function decorateGlossaryIn(rootEl, glossary) {
  if (!rootEl || !glossary || !Array.isArray(glossary.entries)) return;
  glossaryCache = glossary;

  // Prepare regex matchers once per call.
  const matchers = [];
  for (const entry of glossary.entries) {
    if (entry.kind === 'pattern' && entry.pattern) {
      try {
        matchers.push({ entry, re: new RegExp(entry.pattern, 'g') });
      } catch {
        /* ignore bad regex from API */
      }
    } else if (entry.kind === 'term' && entry.term) {
      // Whole-word, case-sensitive (most terms are domain-specific & capitalized
      // intentionally, like "Global Admin"; lowercase ones like "phase" match
      // bare words, which is what we want).
      const escaped = entry.term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      matchers.push({
        entry,
        re: new RegExp(`(^|[^A-Za-z0-9_-])(${escaped})(?=[^A-Za-z0-9_-]|$)`, 'g'),
        hasPrefix: true,
      });
    }
  }
  if (matchers.length === 0) return;

  const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
      let p = node.parentElement;
      while (p && p !== rootEl) {
        if (SKIP_ANCESTORS.has(p.tagName)) return NodeFilter.FILTER_REJECT;
        if (p.classList && p.classList.contains('glossary-term'))
          return NodeFilter.FILTER_REJECT;
        if (p.classList && p.classList.contains('diagram-rendered'))
          return NodeFilter.FILTER_REJECT;
        p = p.parentElement;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });

  const targets = [];
  let n;
  while ((n = walker.nextNode())) targets.push(n);

  for (const textNode of targets) {
    decorateTextNode(textNode, matchers);
  }
}

function decorateTextNode(textNode, matchers) {
  const text = textNode.nodeValue;
  // Find first match across all matchers (earliest start wins).
  let best = null;
  for (const m of matchers) {
    m.re.lastIndex = 0;
    const match = m.re.exec(text);
    if (!match) continue;
    const startInText = m.hasPrefix ? match.index + match[1].length : match.index;
    const matchedText = m.hasPrefix ? match[2] : match[0];
    if (!best || startInText < best.start) {
      best = { entry: m.entry, start: startInText, length: matchedText.length };
    }
  }
  if (!best) return;

  const before = text.slice(0, best.start);
  const middle = text.slice(best.start, best.start + best.length);
  const after = text.slice(best.start + best.length);

  const span = document.createElement('span');
  span.className = 'glossary-term';
  span.dataset.termKey = entryKey(best.entry);
  span.tabIndex = 0;
  span.textContent = middle;

  const parent = textNode.parentNode;
  if (before) parent.insertBefore(document.createTextNode(before), textNode);
  parent.insertBefore(span, textNode);
  if (after) {
    const afterNode = document.createTextNode(after);
    parent.insertBefore(afterNode, textNode);
    parent.removeChild(textNode);
    // Recurse on the trailing part so multiple terms in one text node can decorate.
    decorateTextNode(afterNode, matchers);
  } else {
    parent.removeChild(textNode);
  }
}

function entryKey(entry) {
  if (entry.kind === 'term') return `term:${entry.term}`;
  return `pattern:${entry.pattern}`;
}

function findEntry(key) {
  if (!glossaryCache || !Array.isArray(glossaryCache.entries)) return null;
  return glossaryCache.entries.find((e) => entryKey(e) === key) || null;
}

// ── Tooltip ─────────────────────────────────────────────────────────────────

export function setupGlossaryTooltips() {
  if (tooltipEl) return;
  tooltipEl = document.createElement('div');
  tooltipEl.className = 'glossary-tooltip';
  tooltipEl.setAttribute('role', 'tooltip');
  tooltipEl.hidden = true;
  document.body.appendChild(tooltipEl);

  document.addEventListener('mouseover', onEnter, true);
  document.addEventListener('mouseout', onLeave, true);
  document.addEventListener('focusin', onEnter, true);
  document.addEventListener('focusout', onLeave, true);
  document.addEventListener('scroll', hide, true);
  window.addEventListener('resize', hide);
  window.addEventListener('hashchange', hide);
}

function onEnter(e) {
  const term = e.target.closest && e.target.closest('.glossary-term');
  if (!term) return;
  show(term);
}

function onLeave(e) {
  const term = e.target.closest && e.target.closest('.glossary-term');
  if (!term) return;
  // Hide only if related target is outside the term (avoids flicker when crossing the span).
  if (e.relatedTarget && term.contains(e.relatedTarget)) return;
  hide();
}

function show(term) {
  const entry = findEntry(term.dataset.termKey);
  if (!entry) return;
  tooltipEl.innerHTML = '';

  const header = document.createElement('div');
  header.className = 'glossary-tooltip-header';
  header.textContent = entry.label || entry.term || 'Term';
  tooltipEl.appendChild(header);

  const body = document.createElement('div');
  body.className = 'glossary-tooltip-body';
  body.textContent = entry.body || '';
  tooltipEl.appendChild(body);

  if (entry.href) {
    const link = document.createElement('a');
    link.className = 'glossary-tooltip-link';
    link.href = entry.href;
    link.textContent = 'Open reference →';
    tooltipEl.appendChild(link);
  }

  tooltipEl.hidden = false;
  position(term);
}

function position(term) {
  const rect = term.getBoundingClientRect();
  const tipRect = tooltipEl.getBoundingClientRect();
  const margin = 8;
  let top = rect.bottom + margin;
  let left = rect.left;
  if (left + tipRect.width > window.innerWidth - margin) {
    left = window.innerWidth - tipRect.width - margin;
  }
  if (left < margin) left = margin;
  if (top + tipRect.height > window.innerHeight - margin) {
    // Flip above the term.
    top = rect.top - tipRect.height - margin;
    if (top < margin) top = margin;
  }
  tooltipEl.style.top = `${top}px`;
  tooltipEl.style.left = `${left}px`;
}

function hide() {
  if (tooltipEl) tooltipEl.hidden = true;
}

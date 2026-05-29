/*
 * Mermaid loader — lazy-imports mermaid only when a document contains diagrams.
 * The server emits placeholders of the form:
 *   <figure class="diagram-placeholder" data-diagram="mermaid">
 *     <figcaption>…</figcaption>
 *     <pre><code class="language-mermaid">SOURCE</code></pre>
 *   </figure>
 * We replace each with the rendered SVG (or leave the placeholder + error on failure).
 */

const MERMAID_URL = 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';

let mermaidPromise = null;

function ensureMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import(/* @vite-ignore */ MERMAID_URL)
      .then((m) => {
        const mermaid = m.default || m;
        mermaid.initialize({
          startOnLoad: false,
          theme: 'default',
          securityLevel: 'strict',
          fontFamily:
            'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        });
        return mermaid;
      })
      .catch((err) => {
        // Reset so a later doc can retry.
        mermaidPromise = null;
        throw err;
      });
  }
  return mermaidPromise;
}

export async function renderMermaidIn(rootEl) {
  if (!rootEl) return;
  const figures = rootEl.querySelectorAll(
    'figure.diagram-placeholder[data-diagram="mermaid"]',
  );
  if (figures.length === 0) return;

  let mermaid;
  try {
    mermaid = await ensureMermaid();
  } catch (err) {
    for (const fig of figures) {
      addError(fig, `Mermaid library failed to load: ${err.message || err}`);
    }
    return;
  }

  let idx = 0;
  for (const fig of figures) {
    const codeEl = fig.querySelector('code');
    if (!codeEl) continue;
    const source = codeEl.textContent;
    const id = `mmd-${Date.now()}-${idx++}`;
    try {
      const { svg } = await mermaid.render(id, source);
      const wrapper = document.createElement('figure');
      wrapper.className = 'diagram-rendered';
      wrapper.innerHTML = svg;
      const caption = document.createElement('figcaption');
      caption.className = 'diagram-caption';
      caption.textContent = 'Mermaid diagram';
      wrapper.appendChild(caption);
      fig.replaceWith(wrapper);
    } catch (err) {
      addError(fig, `Mermaid render failed: ${err.message || err}`);
    }
  }
}

function addError(fig, message) {
  const existing = fig.querySelector('.diagram-error');
  if (existing) return;
  const el = document.createElement('div');
  el.className = 'diagram-error';
  el.textContent = message;
  fig.appendChild(el);
}

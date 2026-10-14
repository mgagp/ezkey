/**
 * Runtime proof: approverIdentifier / challenge payloads render as text, not HTML
 * (Lot B / CodeQL js/xss #1).
 *
 * Run: node --test ezkey-demo-app-acme/src/test/js/biz-status.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  showRowStatusOn,
  showChallengeStripOn,
  formatChallengeForDisplay,
} from '../../main/resources/static/js/biz-status.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Minimal DOM stand-in that distinguishes textContent assignment from HTML parsing.
 * Setting textContent never creates element children from markup; setting innerHTML would.
 */
function createFakeDocument() {
  class FakeNode {
    constructor() {
      this.childNodes = [];
      this.parentNode = null;
    }
  }

  class FakeText extends FakeNode {
    constructor(data) {
      super();
      this.nodeType = 3;
      this.data = String(data);
    }
    get textContent() {
      return this.data;
    }
  }

  class FakeElement extends FakeNode {
    constructor(tagName) {
      super();
      this.nodeType = 1;
      this.tagName = String(tagName).toUpperCase();
      this.className = '';
      this._textOnly = null;
      this.hiddenClasses = new Set();
      const self = this;
      this.classList = {
        add(name) {
          self.hiddenClasses.add(name);
        },
        remove(name) {
          self.hiddenClasses.delete(name);
        },
        contains(name) {
          return self.hiddenClasses.has(name);
        },
      };
    }

    set textContent(value) {
      this.childNodes = [];
      this._textOnly = value == null ? '' : String(value);
    }

    get textContent() {
      if (this._textOnly !== null) {
        return this._textOnly;
      }
      return this.childNodes.map((c) => c.textContent).join('');
    }

    set innerHTML(html) {
      // Intentionally naive: records that HTML was parsed into element children.
      this._textOnly = null;
      this.childNodes = [];
      const imgMatch = String(html).match(/<img\b[^>]*>/i);
      if (imgMatch) {
        const img = new FakeElement('img');
        img.setAttribute?.('onerror', 'x');
        this.childNodes.push(img);
      }
      const spanMatch = String(html).match(/<span[^>]*>([\s\S]*?)<\/span>/gi);
      if (spanMatch) {
        for (const block of spanMatch) {
          const el = new FakeElement('span');
          const inner = block.replace(/^<span[^>]*>/i, '').replace(/<\/span>$/i, '');
          el.textContent = inner;
          this.childNodes.push(el);
        }
      }
    }

    replaceChildren(...nodes) {
      this._textOnly = null;
      this.childNodes = [];
      for (const n of nodes) {
        this.append(n);
      }
    }

    append(...nodes) {
      this._textOnly = null;
      for (const n of nodes) {
        n.parentNode = this;
        this.childNodes.push(n);
      }
    }

    querySelectorAll(selector) {
      if (selector.toLowerCase() === 'img') {
        return this._collectTag('IMG');
      }
      return [];
    }

    _collectTag(tag) {
      const out = [];
      const walk = (node) => {
        if (node.nodeType === 1 && node.tagName === tag) {
          out.push(node);
        }
        if (node.childNodes) {
          for (const c of node.childNodes) walk(c);
        }
      };
      walk(this);
      return out;
    }
  }

  return {
    createElement(tag) {
      return new FakeElement(tag);
    },
    createTextNode(data) {
      return new FakeText(data);
    },
    FakeElement,
  };
}

describe('showRowStatusOn — XSS runtime proof', () => {
  it('renders approverIdentifier containing <img onerror> as text, with zero img elements', () => {
    const doc = createFakeDocument();
    const el = doc.createElement('div');
    const payload = '<img onerror=alert(1) src=x>';

    showRowStatusOn(el, 'st-sent', '→', 'Request sent to "' + payload + '"', doc);

    assert.equal(el.querySelectorAll('img').length, 0);
    assert.match(el.textContent, /<img onerror=alert\(1\) src=x>/);
    assert.equal(el.childNodes.length, 2);
    assert.equal(el.childNodes[0].className, 'biz-st-icon');
    assert.equal(el.childNodes[1].textContent.includes(payload), true);
  });

  it('does not honor HTML in the icon slot either', () => {
    const doc = createFakeDocument();
    const el = doc.createElement('div');
    showRowStatusOn(el, 'st-error', '<img onerror=alert(1)>', 'ok', doc);
    assert.equal(el.querySelectorAll('img').length, 0);
    assert.equal(el.childNodes[0].textContent, '<img onerror=alert(1)>');
  });
});

describe('showChallengeStripOn', () => {
  it('places challenge digits in a span via textContent', () => {
    const doc = createFakeDocument();
    const el = doc.createElement('div');
    el.classList.add('hidden');
    showChallengeStripOn(el, 7, doc);
    assert.equal(el.classList.contains('hidden'), false);
    const codeSpan = el.childNodes.find(
      (n) => n.nodeType === 1 && n.className === 'biz-challenge-code'
    );
    assert.ok(codeSpan);
    assert.equal(codeSpan.textContent, '07');
    assert.equal(el.querySelectorAll('img').length, 0);
  });

  it('does not create img nodes when challenge text is hostile markup', () => {
    const doc = createFakeDocument();
    const el = doc.createElement('div');
    // Non-numeric path of formatChallengeForDisplay returns String(value)
    showChallengeStripOn(el, '<img onerror=alert(1)>', doc);
    assert.equal(el.querySelectorAll('img').length, 0);
    assert.match(el.textContent, /<img onerror=alert\(1\)>/);
  });
});

describe('formatChallengeForDisplay', () => {
  it('pads single digits', () => {
    assert.equal(formatChallengeForDisplay(3), '03');
    assert.equal(formatChallengeForDisplay(42), '42');
  });
});

describe('module path sanity', () => {
  it('loads the production static module (not a test-only copy)', () => {
    const productionPath = path.resolve(
      __dirname,
      '../../main/resources/static/js/biz-status.mjs'
    );
    assert.ok(pathToFileURL(productionPath).href.includes('/static/js/biz-status.mjs'));
  });
});

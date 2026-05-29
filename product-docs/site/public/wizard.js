/*
 * Wizard — track state + persistent footer.
 *
 * Public surface:
 *   initWizard({ tracks, onTrackChange, onStepChange })
 *   activate(trackId)            // entering a persona track
 *   deactivate()                 // exit track
 *   next() / prev() / goTo(idx)
 *   getActiveTrack() / getActiveStep()
 *   isFileInActiveTrack(file)    // for tree pastilles
 *   stepIndexForCurrent(file)    // -1 if not in track
 *
 * State persisted in localStorage so refresh keeps the user in the track.
 */

const STORAGE_KEY = 'ezkey-method-explorer:track:v1';

const state = {
  tracks: [],
  byId: new Map(),
  activeTrackId: null,
  activeStepIndex: -1,
  callbacks: {},
  footerEl: null,
  topbarBtns: [],
};

export function initWizard({ tracks, onTrackChange, onStepChange }) {
  state.tracks = Array.isArray(tracks) ? tracks : [];
  state.byId = new Map(state.tracks.map((t) => [t.id, t]));
  state.callbacks = { onTrackChange, onStepChange };
  state.footerEl = document.getElementById('wizard-footer');
  state.topbarBtns = Array.from(document.querySelectorAll('.persona-btn[data-track]'));

  // Enable buttons and wire clicks.
  for (const btn of state.topbarBtns) {
    btn.disabled = false;
    btn.removeAttribute('title');
    btn.addEventListener('click', () => {
      const id = btn.dataset.track;
      if (state.activeTrackId === id) {
        deactivate();
      } else {
        activate(id);
      }
    });
  }

  // Restore previous track from storage (do not auto-navigate; just rehydrate UI).
  const saved = loadSaved();
  if (saved && state.byId.has(saved.trackId)) {
    state.activeTrackId = saved.trackId;
    state.activeStepIndex = Number.isInteger(saved.stepIndex) ? saved.stepIndex : -1;
    syncTopbar();
    renderFooter();
    state.callbacks.onTrackChange?.(getActiveTrack());
  } else {
    renderFooter();
  }
}

export function activate(trackId) {
  const track = state.byId.get(trackId);
  if (!track) return;
  state.activeTrackId = trackId;
  state.activeStepIndex = 0;
  saveState();
  syncTopbar();
  renderFooter();
  state.callbacks.onTrackChange?.(track);
  state.callbacks.onStepChange?.(track, track.steps[0], 0);
}

export function deactivate() {
  state.activeTrackId = null;
  state.activeStepIndex = -1;
  saveState();
  syncTopbar();
  renderFooter();
  state.callbacks.onTrackChange?.(null);
}

export function next() {
  const track = getActiveTrack();
  if (!track) return;
  if (state.activeStepIndex < track.steps.length - 1) {
    state.activeStepIndex++;
    saveState();
    renderFooter();
    state.callbacks.onStepChange?.(track, track.steps[state.activeStepIndex], state.activeStepIndex);
  }
}

export function prev() {
  const track = getActiveTrack();
  if (!track) return;
  if (state.activeStepIndex > 0) {
    state.activeStepIndex--;
    saveState();
    renderFooter();
    state.callbacks.onStepChange?.(track, track.steps[state.activeStepIndex], state.activeStepIndex);
  }
}

export function goTo(idx) {
  const track = getActiveTrack();
  if (!track) return;
  if (idx < 0 || idx >= track.steps.length) return;
  state.activeStepIndex = idx;
  saveState();
  renderFooter();
  state.callbacks.onStepChange?.(track, track.steps[idx], idx);
}

export function getActiveTrack() {
  return state.activeTrackId ? state.byId.get(state.activeTrackId) || null : null;
}

export function getActiveStep() {
  const track = getActiveTrack();
  if (!track || state.activeStepIndex < 0) return null;
  return track.steps[state.activeStepIndex] || null;
}

/** Active step index, or -1. */
export function getActiveStepIndex() {
  return state.activeStepIndex;
}

/**
 * Silently restore track + step without firing any callback. Used to apply
 * `?track=&step=` permalink state coming from the URL on load.
 */
export function setActive(trackId, stepIndex) {
  if (!state.byId.has(trackId)) return;
  state.activeTrackId = trackId;
  const track = state.byId.get(trackId);
  const idx = Number.isInteger(stepIndex) ? stepIndex : 0;
  state.activeStepIndex = Math.max(0, Math.min(idx, track.steps.length - 1));
  saveState();
  syncTopbar();
  renderFooter();
}

export function isFileInActiveTrack(file) {
  const track = getActiveTrack();
  if (!track) return false;
  return track.steps.some((s) => s.file === file);
}

export function stepIndexForFile(file) {
  const track = getActiveTrack();
  if (!track) return -1;
  return track.steps.findIndex((s) => s.file === file);
}

/**
 * Called by app.js when the user navigates manually to a file that is part
 * of the active track. Keep step pointer in sync without re-triggering nav.
 */
export function syncStepFromFile(file) {
  const track = getActiveTrack();
  if (!track) return;
  const idx = track.steps.findIndex((s) => s.file === file);
  if (idx === -1 || idx === state.activeStepIndex) return;
  state.activeStepIndex = idx;
  saveState();
  renderFooter();
}

// ── UI ──────────────────────────────────────────────────────────────────────

function syncTopbar() {
  for (const btn of state.topbarBtns) {
    btn.classList.toggle('active', btn.dataset.track === state.activeTrackId);
  }
}

function renderFooter() {
  const footer = state.footerEl;
  if (!footer) return;
  const track = getActiveTrack();
  if (!track) {
    footer.hidden = true;
    footer.innerHTML = '';
    document.body.classList.remove('has-wizard');
    return;
  }
  document.body.classList.add('has-wizard');
  footer.hidden = false;
  footer.style.setProperty('--track-color', track.color || '#2563eb');

  const idx = Math.max(0, state.activeStepIndex);
  const step = track.steps[idx];
  const total = track.steps.length;

  footer.innerHTML = '';

  // Left: track label + step counter.
  const left = document.createElement('div');
  left.className = 'wizard-left';
  const label = document.createElement('span');
  label.className = 'wizard-track-label';
  label.textContent = track.label;
  const counter = document.createElement('span');
  counter.className = 'wizard-step-counter';
  counter.textContent = `Step ${idx + 1} of ${total}`;
  left.appendChild(label);
  left.appendChild(counter);
  footer.appendChild(left);

  // Center: narration + active file name.
  const center = document.createElement('div');
  center.className = 'wizard-center';
  const narration = document.createElement('div');
  narration.className = 'wizard-narration';
  narration.textContent = step.narration || '';
  center.appendChild(narration);
  const stepNav = document.createElement('div');
  stepNav.className = 'wizard-step-nav';
  for (let i = 0; i < total; i++) {
    const dot = document.createElement('button');
    dot.type = 'button';
    dot.className = 'wizard-dot' + (i === idx ? ' active' : '');
    dot.title = `Step ${i + 1}: ${track.steps[i].file}`;
    dot.addEventListener('click', () => goTo(i));
    stepNav.appendChild(dot);
  }
  center.appendChild(stepNav);
  footer.appendChild(center);

  // Right: prev / next / exit / copy link.
  const right = document.createElement('div');
  right.className = 'wizard-right';
  const prevBtn = mkBtn('← Prev', () => prev());
  prevBtn.disabled = idx === 0;
  const nextBtn = mkBtn('Next →', () => next());
  nextBtn.disabled = idx === total - 1;
  nextBtn.classList.add('primary');
  const copyBtn = mkBtn('Copy link', () => copyStepLink(track, idx, copyBtn));
  copyBtn.classList.add('subtle');
  copyBtn.title = 'Copy a deep link to this step';
  const exitBtn = mkBtn('Exit track', () => deactivate());
  exitBtn.classList.add('subtle');
  right.appendChild(prevBtn);
  right.appendChild(nextBtn);
  right.appendChild(copyBtn);
  right.appendChild(exitBtn);
  footer.appendChild(right);
}

function copyStepLink(track, idx, btn) {
  const step = track.steps[idx];
  if (!step) return;
  const base = `${window.location.origin}${window.location.pathname}`;
  const query = [
    step.anchor ? `h=${encodeURIComponent(step.anchor)}` : null,
    `track=${encodeURIComponent(track.id)}`,
    `step=${idx}`,
  ].filter(Boolean).join('&');
  const url = `${base}#/${step.file}?${query}`;
  const showOk = () => {
    const original = btn.textContent;
    btn.textContent = 'Copied!';
    setTimeout(() => { btn.textContent = original; }, 1200);
  };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(showOk).catch(() => fallbackCopy(url, showOk));
  } else {
    fallbackCopy(url, showOk);
  }
}

function fallbackCopy(text, onDone) {
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  try { document.execCommand('copy'); onDone(); } catch { /* ignore */ }
  document.body.removeChild(ta);
}

function mkBtn(label, onClick) {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = 'wizard-btn';
  b.textContent = label;
  b.addEventListener('click', onClick);
  return b;
}

// ── Persistence ─────────────────────────────────────────────────────────────

function saveState() {
  try {
    if (!state.activeTrackId) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ trackId: state.activeTrackId, stepIndex: state.activeStepIndex }),
      );
    }
  } catch {
    /* ignore quota */
  }
}

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

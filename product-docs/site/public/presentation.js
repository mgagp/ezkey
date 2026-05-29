/*
 * Ezkey Method · Local Explorer — Presentation mode (Phase 3).
 *
 * Press F to toggle fullscreen + a stripped-down presentation skin (hides rails
 * and tree, enlarges typography, keeps the phase ribbon + wizard footer for
 * narration). Arrow keys ← → navigate steps in the active wizard track.
 * Esc exits both fullscreen and presentation mode.
 */

import { prev as wizardPrev, next as wizardNext, getActiveTrack } from './wizard.js';

let isOn = false;

function shouldIgnoreKey(e) {
  const t = e.target;
  if (!t) return false;
  const tag = (t.tagName || '').toLowerCase();
  return tag === 'input' || tag === 'textarea' || t.isContentEditable === true;
}

export function isPresentationOn() { return isOn; }

export function enter() {
  if (isOn) return;
  isOn = true;
  document.body.classList.add('is-presentation');
  if (document.documentElement.requestFullscreen) {
    document.documentElement.requestFullscreen().catch(() => { /* user can decline */ });
  }
}

export function exit() {
  if (!isOn) return;
  isOn = false;
  document.body.classList.remove('is-presentation');
  if (document.fullscreenElement && document.exitFullscreen) {
    document.exitFullscreen().catch(() => { /* ignore */ });
  }
}

export function toggle() { isOn ? exit() : enter(); }

export function setupPresentationShortcuts() {
  document.addEventListener('keydown', (e) => {
    if (shouldIgnoreKey(e)) return;

    if ((e.key === 'f' || e.key === 'F') && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      toggle();
      return;
    }
    if (e.key === 'Escape' && isOn) {
      // Escape exits presentation (fullscreen will exit on its own too).
      exit();
      return;
    }
    // Arrow navigation in the active track. Active in presentation mode,
    // and also outside (convenient even without fullscreen) when a track is on.
    if (e.key === 'ArrowRight' && getActiveTrack()) {
      e.preventDefault();
      wizardNext();
      return;
    }
    if (e.key === 'ArrowLeft' && getActiveTrack()) {
      e.preventDefault();
      wizardPrev();
      return;
    }
  });

  // If the user exits fullscreen via system UI, also drop presentation skin.
  document.addEventListener('fullscreenchange', () => {
    if (!document.fullscreenElement && isOn) {
      isOn = false;
      document.body.classList.remove('is-presentation');
    }
  });
}

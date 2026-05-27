/*
 * Ezkey Method · Local Explorer — Cognitive map view (Phase 3).
 *
 * Renders a one-page SVG of the workflow phases as a second entry point.
 * Clicking a phase opens the canonical doc for that phase. Two parallel
 * lanes (Plan incubation, Legacy retrofit) sit alongside the main flow.
 *
 * Phase ids and colors must stay aligned with phases.json. Doc anchors below
 * pick the most useful entry point per phase.
 */

const MAIN_FLOW = [
  { id: 'vision',      label: 'Vision',           color: '#7c3aed', file: 'methodology/methodological-values.md' },
  { id: 'capture',     label: 'Capture',          color: '#0891b2', file: 'methodology/blitz-intake-pattern.md' },
  { id: 'challenge',   label: 'Challenge',        color: '#d97706', file: 'methodology/workflow-overview.md' },
  { id: 'promote',     label: 'Tracer Bullet',    color: '#2563eb', file: 'methodology/tracer-bullet-method.md' },
  { id: 'design',      label: 'Design',           color: '#4f46e5', file: 'methodology/analysis-and-design-canon.md' },
  { id: 'test',        label: 'Test Strategy',    color: '#0d9488', file: 'methodology/testing-strategy-in-workflow.md' },
  { id: 'gate',        label: 'Quality Gates',    color: '#16a34a', file: 'methodology/quality-gates.md' },
];

const PARALLEL_LANES = [
  { id: 'incubation',    label: 'Plan incubation',     color: '#0284c7', file: 'methodology/plan-incubation-workflow.md' },
  { id: 'retrofit',      label: 'Legacy retrofit',     color: '#64748b', file: 'methodology/legacy-retrofit-workflow.md' },
  { id: 'collaboration', label: 'AI collaboration',    color: '#e11d48', file: 'methodology/ai-collaboration-model.md' },
];

export function renderMap(rootEl) {
  rootEl.innerHTML = '';

  const wrap = document.createElement('div');
  wrap.className = 'map-wrap';

  const h1 = document.createElement('h1');
  h1.textContent = 'Workflow map';
  wrap.appendChild(h1);

  const lead = document.createElement('p');
  lead.className = 'map-lead';
  lead.textContent =
    'The main flow runs left to right. Two parallel lanes (plan incubation, legacy retrofit) feed into it; AI collaboration is the cross-cutting working model. Click any node to open its canonical document.';
  wrap.appendChild(lead);

  wrap.appendChild(buildSvg());

  const legend = document.createElement('div');
  legend.className = 'map-legend';
  legend.innerHTML =
    '<span class="map-legend-item"><span class="map-legend-dot" style="background:#94a3b8"></span> click any node to open its doc</span>' +
    '<span class="map-legend-item"><span class="map-legend-dot" style="background:#0284c7"></span> parallel lanes feed the main flow</span>';
  wrap.appendChild(legend);

  rootEl.appendChild(wrap);
}

function buildSvg() {
  const W = 980;
  const H = 460;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.setAttribute('class', 'map-svg');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', 'Workflow phases map');

  // Layout: main flow centered vertically, parallel lanes above and below.
  const mainY = 220;
  const topY = 80;
  const botY = 360;
  const padL = 40;
  const padR = 40;
  const nodeW = 120;
  const nodeH = 56;
  const stepX = (W - padL - padR - nodeW) / (MAIN_FLOW.length - 1);

  // Main-flow connectors first (so they sit under the nodes).
  for (let i = 0; i < MAIN_FLOW.length - 1; i++) {
    const x1 = padL + i * stepX + nodeW;
    const x2 = padL + (i + 1) * stepX;
    const y = mainY + nodeH / 2;
    svg.appendChild(line(x1, y, x2, y, '#cbd5e1', 2));
  }

  // Main-flow nodes.
  MAIN_FLOW.forEach((p, i) => {
    const x = padL + i * stepX;
    svg.appendChild(nodeGroup(x, mainY, nodeW, nodeH, p));
  });

  // Parallel lanes — Plan incubation above, feeding Capture → Tracer Bullet.
  const inc = PARALLEL_LANES[0];
  const incX = padL + 1 * stepX + nodeW * 0.2;
  svg.appendChild(nodeGroup(incX, topY, nodeW, nodeH, inc));
  svg.appendChild(curve(incX + nodeW / 2, topY + nodeH, padL + 3 * stepX + nodeW / 2, mainY, inc.color));

  // Legacy retrofit below, feeding into Capture / Promote.
  const ret = PARALLEL_LANES[1];
  const retX = padL + 1 * stepX + nodeW * 0.2;
  svg.appendChild(nodeGroup(retX, botY, nodeW, nodeH, ret));
  svg.appendChild(curve(retX + nodeW / 2, botY, padL + 1 * stepX + nodeW / 2, mainY + nodeH, ret.color));

  // AI collaboration — cross-cutting badge bottom-right.
  const aic = PARALLEL_LANES[2];
  const aicX = padL + 5 * stepX + nodeW * 0.2;
  svg.appendChild(nodeGroup(aicX, botY, nodeW, nodeH, aic));
  svg.appendChild(curve(aicX + nodeW / 2, botY, padL + 5 * stepX + nodeW / 2, mainY + nodeH, aic.color));

  return svg;
}

function nodeGroup(x, y, w, h, phase) {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  g.setAttribute('class', 'map-node');
  g.setAttribute('tabindex', '0');
  g.setAttribute('role', 'link');
  g.setAttribute('aria-label', phase.label);
  g.style.cursor = 'pointer';

  const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  rect.setAttribute('x', x);
  rect.setAttribute('y', y);
  rect.setAttribute('width', w);
  rect.setAttribute('height', h);
  rect.setAttribute('rx', '10');
  rect.setAttribute('ry', '10');
  rect.setAttribute('fill', '#ffffff');
  rect.setAttribute('stroke', phase.color);
  rect.setAttribute('stroke-width', '2');
  g.appendChild(rect);

  const dot = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  dot.setAttribute('cx', x + 14);
  dot.setAttribute('cy', y + h / 2);
  dot.setAttribute('r', '5');
  dot.setAttribute('fill', phase.color);
  g.appendChild(dot);

  const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
  text.setAttribute('x', x + 28);
  text.setAttribute('y', y + h / 2 + 5);
  text.setAttribute('font-size', '14');
  text.setAttribute('font-family', 'system-ui, -apple-system, Segoe UI, sans-serif');
  text.setAttribute('fill', '#0f172a');
  text.textContent = phase.label;
  g.appendChild(text);

  g.addEventListener('click', () => {
    window.location.hash = `#/${phase.file}`;
  });
  g.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      window.location.hash = `#/${phase.file}`;
    }
  });

  return g;
}

function line(x1, y1, x2, y2, color, width) {
  const l = document.createElementNS('http://www.w3.org/2000/svg', 'line');
  l.setAttribute('x1', x1);
  l.setAttribute('y1', y1);
  l.setAttribute('x2', x2);
  l.setAttribute('y2', y2);
  l.setAttribute('stroke', color);
  l.setAttribute('stroke-width', width);
  return l;
}

function curve(x1, y1, x2, y2, color) {
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  const midY = (y1 + y2) / 2;
  const d = `M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`;
  p.setAttribute('d', d);
  p.setAttribute('fill', 'none');
  p.setAttribute('stroke', color);
  p.setAttribute('stroke-width', '1.5');
  p.setAttribute('stroke-dasharray', '4 4');
  p.setAttribute('opacity', '0.7');
  return p;
}

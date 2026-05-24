<#
.SYNOPSIS
    Publishes the Ezkey Methodology rich view to the ezkey-org static site.
.DESCRIPTION
    Reads product-docs/methodology/view/index.html (the canonical source), extracts
    its <nav>, <header>, <main>, and <footer> blocks, injects them into a site shell,
    applies the ezkey.org visual palette, strips internal .md links, and writes
    sites/ezkey-org/methodology.html (UTF-8 without BOM).

    Run again after updating the canonical to refresh the published page.
.PARAMETER DryRun
    When specified, prints what would be written but does not touch the output file.
.EXAMPLE
    .\scripts\publish-methodology-view.ps1
.EXAMPLE
    .\scripts\publish-methodology-view.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot      = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$canonicalPath = Join-Path $repoRoot 'product-docs\methodology\view\index.html'
$outputPath    = Join-Path $repoRoot 'sites\ezkey-org\methodology.html'
$syncDate      = Get-Date -Format 'yyyy-MM-dd'
$rx            = [System.Text.RegularExpressions.Regex]
$rxOpts        = [System.Text.RegularExpressions.RegexOptions]::Singleline

# ---------------------------------------------------------------------------
# 1. Read canonical source
# ---------------------------------------------------------------------------
Write-Host "Reading canonical:  $canonicalPath"
$src = [System.IO.File]::ReadAllText($canonicalPath, [System.Text.UTF8Encoding]::new($false))

# ---------------------------------------------------------------------------
# 2. Extract blocks
# ---------------------------------------------------------------------------
$styleInner  = $rx::Match($src, '(?s)<style>(.*?)</style>', $rxOpts).Groups[1].Value
if (-not $styleInner)  { throw 'Could not extract <style> block from canonical.' }

$navBlock    = $rx::Match($src, '(?s)<nav>.*?</nav>', $rxOpts).Value
if (-not $navBlock)    { throw 'Could not extract <nav> block from canonical.' }

$headerBlock = $rx::Match($src, '(?s)<header class="page-header">.*?</header>', $rxOpts).Value
if (-not $headerBlock) { throw 'Could not extract <header class="page-header"> block.' }

$mainBlock   = $rx::Match($src, '(?s)<main>.*?</main>', $rxOpts).Value
if (-not $mainBlock)   { throw 'Could not extract <main> block from canonical.' }

$footerBlock = $rx::Match($src, '(?s)<footer>.*?</footer>', $rxOpts).Value
if (-not $footerBlock) { throw 'Could not extract <footer> block from canonical.' }

# ---------------------------------------------------------------------------
# 3. Clean internal .md links (strip href, keep visible text)
#    These are private-repo links that must not appear on the public site.
# ---------------------------------------------------------------------------
$cleanMdLinks = { param([string]$s)
    $s -replace '(?s)<a href="[^"]*\.md[^"]*">([^<]*)</a>', '$1'
}
$navBlock    = & $cleanMdLinks $navBlock
$headerBlock = & $cleanMdLinks $headerBlock
$mainBlock   = & $cleanMdLinks $mainBlock
$footerBlock = & $cleanMdLinks $footerBlock

# ---------------------------------------------------------------------------
# 4. Strip content between pub-exclude markers (roadmap/self-referential items
#    kept in the canonical but omitted from the public-facing published pages)
# ---------------------------------------------------------------------------
$mainBlock = $rx::Replace($mainBlock,
    '(?s)\s*<!--\s*pub-exclude-start\s*-->.*?<!--\s*pub-exclude-end\s*-->', '', $rxOpts)

# ---------------------------------------------------------------------------
# 5. Site palette override CSS
#    Overrides canonical CSS variables and rules to match ezkey.org palette.
#    --surface and phase card colors are intentionally kept as-is.
# ---------------------------------------------------------------------------
$paletteOverride = @'
    /* ── Site palette overrides ──────────────────────────────────────────── */
    :root {
      --blue: #667eea;
      --indigo: #764ba2;
    }
    body { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
    nav {
      background: rgba(255,255,255,0.97);
      border-bottom-color: rgba(102,126,234,0.25);
    }
    nav a:hover { color: #667eea; }
    .nav-brand span { color: #667eea; }
    .page-header { background: transparent; }
    .section-intro a { color: #667eea; }
    .lane-a-tag { background: #f0f0ff; border-color: #c4c8f0; color: #5561d4; }
    section > h2 { border-bottom-color: rgba(102,126,234,0.3); }
    /* Site attribution footer (outside white card — on gradient background) */
    footer.site-footer {
      color: rgba(255,255,255,0.75);
      border-top: none;
      background: transparent;
      padding: 16px 0;
      margin-top: 24px;
      font-size: 0.82rem;
    }
    footer.site-footer a { color: rgba(255,255,255,0.9); text-decoration: none; }
'@

# ---------------------------------------------------------------------------
# 6. Assemble the output page
# ---------------------------------------------------------------------------
$output = @"
<!DOCTYPE html>
<!-- Source: product-docs/methodology/view/index.html @ $syncDate -->
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="description" content="Visual reference for the ideation-to-delivery workflow &mdash; from raw idea to closed feature, with artifact types, parallel lanes, naming conventions, and skills.">
  <meta property="og:title" content="Ezkey Methodology &mdash; Rich View">
  <meta property="og:description" content="Visual reference for the ideation-to-delivery workflow &mdash; from raw idea to closed feature.">
  <meta property="og:url" content="https://ezkey.org/methodology.html">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="Ezkey">
  <meta property="og:locale" content="en_US">
  <link rel="alternate" hreflang="en" href="https://ezkey.org/methodology.html">
  <link rel="alternate" hreflang="fr" href="https://ezkey.org/fr/methodologie.html">
  <link rel="alternate" hreflang="x-default" href="https://ezkey.org/methodology.html">
  <title>Ezkey Methodology &mdash; Rich View</title>
  <style>
    /* ── Site shell ──────────────────────────────────────────────────────── */
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      min-height: 100vh;
      color: #0f172a;
    }
    .shell {
      max-width: 1460px;
      margin: 0 auto;
      padding: 0 20px 56px;
    }
    .nav-top {
      padding: 14px 0 8px;
      font-size: 0.88rem;
      color: rgba(255,255,255,0.9);
    }
    .nav-top a { color: rgba(255,255,255,0.9); text-decoration: underline; }
    .nav-top a:hover { color: #fff; }
    .site-card {
      background: #fff;
      border-radius: 16px;
      box-shadow: 0 24px 60px rgba(0,0,0,0.18);
      overflow: hidden;
      margin-top: 8px;
    }
    @media (max-width: 640px) {
      .shell { padding: 0 12px 48px; }
      .site-card { border-radius: 12px; }
    }
    /* ── Canonical styles (from product-docs/methodology/view/index.html) ── */
$styleInner
$paletteOverride
  </style>
</head>
<body>
  <div class="shell">
    <p class="nav-top">
      <a href="/">Home (English)</a>
      &middot; <a href="/fr/methodologie.html">Version fran&ccedil;aise</a>
    </p>

$navBlock

    <div class="site-card">
$headerBlock
$mainBlock
$footerBlock
    </div>

    <footer class="site-footer" style="text-align:center;">
      Ezkey &nbsp;&middot;&nbsp; <a href="/">ezkey.org</a>
    </footer>

  </div>
</body>
</html>
"@

# ---------------------------------------------------------------------------
# 7. Write EN page
# ---------------------------------------------------------------------------
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
if ($DryRun) {
    Write-Host "[DryRun] Would write $($output.Length) chars to: $outputPath"
} else {
    [System.IO.File]::WriteAllText($outputPath, $output, $utf8NoBom)
    Write-Host "Published EN: $outputPath"
}

# ---------------------------------------------------------------------------
# 8. French translations
#    All FR content is defined as single-quoted here-strings (no interpolation)
#    so French characters are preserved as literal UTF-8.
# ---------------------------------------------------------------------------
$frNavBlock = @'
<nav>
  <div class="nav-brand">Ezkey <span>M&eacute;thodologie</span></div>
  <div class="nav-sep"></div>
  <a href="#workflow">Flux de travail</a>
  <a href="#artifacts">Artefacts</a>
  <a href="#lanes">Couloirs</a>
  <a href="#collab">Collab</a>
  <a href="#naming">Nommage</a>
  <a href="#skills">Comp&eacute;tences</a>
  <a href="#rich-views">Vues riches</a>
</nav>
'@

$frHeaderBlock = @'
<header class="page-header">
  <h1>M&eacute;thodologie Ezkey</h1>
  <p class="subtitle">
    R&eacute;f&eacute;rence visuelle pour le flux de bout en bout &mdash; de l&rsquo;id&eacute;e brute &agrave; la fonctionnalit&eacute; livr&eacute;e,
    avec les types d&rsquo;artefacts, les couloirs parall&egrave;les, les conventions de nommage et les comp&eacute;tences.
  </p>
  <div class="meta">
    <span>Source&nbsp;: product-docs/methodology/README.md</span>
    <span>D&eacute;cision&nbsp;: 2026-05-24-rich-views</span>
    <span>Mis &agrave; jour le 2026-05-24</span>
  </div>
</header>
'@

$frFooterBlock = @'
<footer>
  M&eacute;thodologie Ezkey Vue riche &nbsp;&middot;&nbsp; Derni&egrave;re mise &agrave; jour le 2026-05-24 &nbsp;&middot;&nbsp;
  Source&nbsp;: product-docs/methodology/README.md
  &nbsp;&middot;&nbsp;
  D&eacute;cision&nbsp;: 2026-05-24-rich-views
</footer>
'@

$frMainBlock = @'
<main>

  <!-- SECTION 1 — FLUX DE TRAVAIL DE BOUT EN BOUT -->
  <section id="workflow">
    <h2>Flux de travail de bout en bout <span class="lane-a-tag">Couloir A</span></h2>
    <p class="section-intro">
      Neuf phases explicites avec sorties d&rsquo;artefacts et crit&egrave;res de sortie. Passez &agrave;
      l&rsquo;impl&eacute;mentation d&egrave;s que la direction, la premi&egrave;re approche, les crit&egrave;res de validation
      et les questions ouvertes non bloquantes sont &eacute;tablis &mdash; en &eacute;vitant autant le code
      pr&eacute;matur&eacute; que la pr&eacute;paration perp&eacute;tuelle.
    </p>

    <div class="flow-scroll">
      <div class="flow-inner">

        <div class="phase-card ph1">
          <div class="phase-num">Phase 1</div>
          <div class="phase-name">Capture</div>
          <div class="phase-action">Enregistrez l&rsquo;id&eacute;e brute avec une intention claire et des tags initiaux. Gardez la premi&egrave;re version courte et expressive.</div>
          <span class="phase-badge bg-i">I-*</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: intention claire et tags assign&eacute;s.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph2">
          <div class="phase-num">Phase 2</div>
          <div class="phase-name">Triage</div>
          <div class="phase-action">Clarifiez l&rsquo;intention, la valeur utilisateur, le risque et la port&eacute;e approximative. Assignez le statut, la priorit&eacute;, les tags de phase et de composant.</div>
          <span class="phase-badge bg-i">I-* mis &agrave; jour</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: port&eacute;e et valeur compr&eacute;hensibles.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph3">
          <div class="phase-num">Phase 3</div>
          <div class="phase-name">Challenge</div>
          <div class="phase-action">Passe de questionnement structur&eacute; (&laquo;&nbsp;Grill Me&nbsp;&raquo;). Faites &eacute;merger les hypoth&egrave;ses, exceptions, chemins d&rsquo;erreur et non-objectifs.</div>
          <span class="phase-badge bg-i">I-* + questions ouvertes</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: risques cl&eacute;s et exceptions explicites.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph4">
          <div class="phase-num">Phase 4</div>
          <div class="phase-name">Promouvoir</div>
          <div class="phase-action">D&eacute;finissez la premi&egrave;re tranche verticale et les preuves attendues. &Eacute;vitez d&rsquo;ajouter de la pr&eacute;paration si la direction est d&eacute;j&agrave; claire.</div>
          <span class="phase-badge bg-tb">TB-*</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: une tranche verticale est testable.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph5">
          <div class="phase-num">Phase 5</div>
          <div class="phase-name">Analyser &amp; Concevoir</div>
          <div class="phase-action">Analyse globale + analyse par composant pour chaque fronti&egrave;re impact&eacute;e. D&eacute;finissez les responsabilit&eacute;s, contrats, validation, comportement d&rsquo;erreur.</div>
          <span class="phase-badge bg-br">briefs + mappings</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: fronti&egrave;res et points de d&eacute;cision explicites.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph6">
          <div class="phase-num">Phase 6</div>
          <div class="phase-name">Planifier les tests</div>
          <div class="phase-action">S&eacute;lectionnez les couches de test minimales et optionnelles&nbsp;: unitaire, fonctionnel, &eacute;lectif, op&eacute;rationnel, UI. Gardez la s&eacute;lection bas&eacute;e sur les risques et les co&ucirc;ts.</div>
          <span class="phase-badge bg-ts">plan de test</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: couches de test minimales explicitement s&eacute;lectionn&eacute;es.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph7">
          <div class="phase-num">Phase 7</div>
          <div class="phase-name">Passerelle</div>
          <div class="phase-action">Appliquez les passerelles qualit&eacute;&nbsp;: contrats, tests, docs, tra&ccedil;abilit&eacute;. V&eacute;rifiez les contr&ocirc;les obligatoires avant l&rsquo;impl&eacute;mentation.</div>
          <span class="phase-badge bg-st">rapport de passerelle</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: les contr&ocirc;les qualit&eacute; obligatoires sont valid&eacute;s.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph8">
          <div class="phase-num">Phase 8</div>
          <div class="phase-name">Impl&eacute;menter</div>
          <div class="phase-action">Ex&eacute;cutez le plan en code et tests. Mettez &agrave; jour les docs et contrats dans le m&ecirc;me changeset. Toute modification de contr&ocirc;leur implique une revue de contrat.</div>
          <span class="phase-badge bg-cd">code + tests</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: preuves compl&egrave;tes et passerelles vertes.</div>
        </div>
        <div class="flow-arrow">&rarr;</div>

        <div class="phase-card ph9">
          <div class="phase-num">Phase 9</div>
          <div class="phase-name">Cl&ocirc;turer</div>
          <div class="phase-action">Mettez &agrave; jour les docs de fonctionnalit&eacute; et de tra&ccedil;abilit&eacute;. Faites la transition du statut du backlog et du tracer bullet. Enregistrez le r&eacute;sum&eacute; de session.</div>
          <span class="phase-badge bg-st">statut + tra&ccedil;abilit&eacute;</span>
          <div class="phase-exit">Termin&eacute; quand&nbsp;: statut et tra&ccedil;abilit&eacute; mis &agrave; jour.</div>
        </div>

      </div>
    </div>
  </section>

  <!-- SECTION 2 — TYPES D'ARTEFACTS -->
  <section id="artifacts">
    <h2>Types d&rsquo;artefacts</h2>
    <p class="section-intro">
      Tous les artefacts canoniques utilisent des identifiants date+slug. Aucune recherche de
      compteur global n&rsquo;est requise. Les identifiants sont stables &mdash; jamais renomm&eacute;s apr&egrave;s
      cr&eacute;ation, jamais r&eacute;utilis&eacute;s.
    </p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Type</th>
            <th>Format d&rsquo;identifiant</th>
            <th>Emplacement</th>
            <th>Cr&eacute;&eacute; lors de</th>
            <th>Cycle de vie du statut</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><span class="ab ab-v">V-*</span>&nbsp; Note de vision</td>
            <td><code>V-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>global/vision/</code></td>
            <td>Capture / Triage</td>
            <td>brouillon &rarr; actif &rarr; remplac&eacute;</td>
          </tr>
          <tr>
            <td><span class="ab ab-i">I-*</span>&nbsp; Id&eacute;e de backlog</td>
            <td><code>I-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>global/backlog/ideas/</code></td>
            <td>Capture</td>
            <td>capt&eacute;e &rarr; tri&eacute;e &rarr; en incubation &rarr; promue &rarr; impl&eacute;ment&eacute;e / diff&eacute;r&eacute;e / abandonn&eacute;e</td>
          </tr>
          <tr>
            <td><span class="ab ab-tb">TB-*</span>&nbsp; Tracer bullet</td>
            <td><code>TB-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>global/backlog/</code></td>
            <td>Promouvoir</td>
            <td>d&eacute;limit&eacute;e &rarr; en cours &rarr; en validation &rarr; ferm&eacute;e</td>
          </tr>
          <tr>
            <td><span class="ab ab-r">R-*</span>&nbsp; Tranche de retrofit</td>
            <td><code>R-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>global/legacy-retrofit/</code></td>
            <td>Couloir retrofit</td>
            <td>en attente &rarr; extraite &rarr; mapp&eacute;e &rarr; ferm&eacute;e</td>
          </tr>
          <tr>
            <td><span class="ab ab-md">plan</span>&nbsp; Plan de travail</td>
            <td>nom libre</td>
            <td><code>plans/</code> ou <code>.cursor/plans/</code></td>
            <td>Couloir incubation de plan</td>
            <td>Non canonique &mdash; mat&eacute;rialis&eacute; en V-*/I-*/TB-*</td>
          </tr>
          <tr>
            <td><span class="ab ab-md">blitz</span>&nbsp; Brouillon blitz</td>
            <td><code>_blitz-YYYY-MM-DD[-slug].md</code></td>
            <td><code>global/backlog/</code></td>
            <td>Blitz intake</td>
            <td>Actif (pr&eacute;fixe <code>_</code>) &rarr; archiv&eacute; (sans pr&eacute;fixe, dans <code>blitz-archive/</code>)</td>
          </tr>
          <tr>
            <td><span class="ab ab-md">grill</span>&nbsp; Session grill</td>
            <td><code>&lt;sujet&gt;-grill-me.md</code></td>
            <td><code>global/backlog/grill-sessions/</code></td>
            <td>Challenge</td>
            <td>ouverte &rarr; reprise &rarr; r&eacute;solue</td>
          </tr>
          <tr>
            <td><span class="ab ab-md">dec</span>&nbsp; D&eacute;cision de m&eacute;thodologie</td>
            <td><code>YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>methodology/decisions/</code></td>
            <td>N&rsquo;importe quand &mdash; quand une r&egrave;gle de m&eacute;thodologie non &eacute;vidente est adopt&eacute;e</td>
            <td>Stable &mdash; jamais r&eacute;vis&eacute;e en place (remplacer avec un nouveau fichier)</td>
          </tr>
          <tr>
            <td><span class="ab ab-rv">RV</span>&nbsp; Vue riche</td>
            <td>dossier slug</td>
            <td><code>&lt;parent&gt;/view/&lt;slug&gt;/index.html</code></td>
            <td>N&rsquo;importe quand &mdash; quand la richesse visuelle am&eacute;liore mat&eacute;riellement la clart&eacute;</td>
            <td>Compagnon de son parent Markdown &mdash; mis &agrave; jour avec lui</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <!-- SECTION 3 — COULOIRS PARALLÈLES -->
  <section id="lanes">
    <h2>Couloirs parall&egrave;les</h2>
    <p class="section-intro">
      Deux couloirs s&rsquo;ex&eacute;cutent en parall&egrave;le du flux principal id&eacute;ation-livraison. Une session
      peut combiner le couloir A avec l&rsquo;un ou les deux couloirs parall&egrave;les.
    </p>
    <div class="lanes-grid">

      <div class="lane-card" style="border-left-color: #7c3aed;">
        <div class="lane-label">Couloir B</div>
        <div class="lane-name" style="color: #7c3aed;">Incubation de plan</div>
        <ul class="lane-steps">
          <li>Cr&eacute;ez ou faites &eacute;voluer un plan de travail dans <code>plans/</code></li>
          <li>Explorez les options et convergez vers une direction (forme libre)</li>
          <li>Mat&eacute;rialisez le signal durable dans V-*/I-*/TB-*</li>
          <li>Reliez le plan et les artefacts canoniques si utile</li>
          <li>Ne traitez pas comme du retrofit sauf si genuinement historique</li>
        </ul>
        <p class="lane-ref">&rarr; plan-incubation-workflow.md</p>
      </div>

      <div class="lane-card" style="border-left-color: #d97706;">
        <div class="lane-label">Couloir C</div>
        <div class="lane-name" style="color: #d97706;">Retrofit historique</div>
        <ul class="lane-steps">
          <li>S&eacute;lectionnez un petit lot source (plans historiques, historique verbal)</li>
          <li>Extrayez les d&eacute;cisions, invariants, patterns, signal de test</li>
          <li>Mappez dans les docs canoniques</li>
          <li>Enregistrez une tranche de retrofit (R-*)</li>
          <li>Cl&ocirc;turez avec les lacunes r&eacute;siduelles et l&rsquo;action suivante</li>
        </ul>
        <p class="lane-ref">&rarr; legacy-retrofit-workflow.md</p>
      </div>

    </div>
  </section>

  <!-- CONTEXTE DE COLLABORATION -->
  <section id="collab">
    <h2>Contexte de collaboration</h2>
    <p class="section-intro">
      Pas un couloir &mdash; un modificateur transversal. Ces r&egrave;gles s&rsquo;appliquent en plus des couloirs
      A, B et C lorsque la collaboration se fait sur des branches Git parall&egrave;les ou des worktrees.
      Les sessions sur une seule branche n&rsquo;ont pas besoin de les appliquer.
    </p>
    <div class="collab-card">
      <div class="collab-modifier-tag">Transversal &middot; s&rsquo;applique aux couloirs A, B, C</div>
      <p class="collab-desc">
        Quand deux d&eacute;veloppeurs (ou un d&eacute;veloppeur sur deux worktrees) travaillent simultan&eacute;ment,
        le nommage ordinal classique cr&eacute;e des collisions &agrave; la fusion. Le contexte de collaboration
        r&eacute;sout ce probl&egrave;me sans surcharge de coordination, en rendant les noms d&rsquo;artefacts
        auto-suffisants gr&acirc;ce aux identifiants date+slug et en diff&eacute;rant les index partag&eacute;s
        apr&egrave;s la fusion.
      </p>
      <div class="collab-rules">
        <div class="collab-rule">
          <strong>IDs date+slug obligatoires</strong>
          Aucune recherche de compteur, aucune contention entre branches.
        </div>
        <div class="collab-rule">
          <strong>Cr&eacute;er des artefacts librement sur la branche</strong>
          V-*, I-*, TB-*, R-* &mdash; aucune coordination inter-branches n&eacute;cessaire.
        </div>
        <div class="collab-rule">
          <strong>Slug blitz obligatoire</strong>
          Sur une branche de fonctionnalit&eacute;, le slug de th&egrave;me est choisi au d&eacute;but de la session &mdash; pas l&rsquo;identifiant ordinal.
        </div>
        <div class="collab-rule">
          <strong>Diff&eacute;rer les mises &agrave; jour d&rsquo;index</strong>
          <code>backlog/index.md</code>, <code>vision/product-orientation-notes.md</code> &mdash; mettez &agrave; jour apr&egrave;s la fusion sur <code>main</code>.
        </div>
        <div class="collab-rule">
          <strong>R&eacute;conciliation de l&rsquo;index &agrave; la fusion</strong>
          Ex&eacute;cutez une passe de r&eacute;conciliation quand la branche arrive sur <code>main</code>.
        </div>
      </div>
      <p class="lane-ref">&rarr; multi-branch-workflow.md</p>
    </div>
  </section>

  <!-- SECTION 4 — CONVENTIONS DE NOMMAGE -->
  <section id="naming">
    <h2>Conventions de nommage</h2>
    <p class="section-intro">
      Identifiants stables et r&eacute;sistants aux collisions sur toutes les branches et worktrees.
      Les artefacts <code>NNNN</code> h&eacute;rit&eacute;s ne sont jamais renomm&eacute;s.
    </p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Artefact</th>
            <th>Nom de fichier actif</th>
            <th>Archiv&eacute; / final</th>
            <th>R&egrave;gle cl&eacute;</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Note de vision</td>
            <td><code>V-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>M&ecirc;me (stable)</td>
            <td>Jamais renomm&eacute; apr&egrave;s cr&eacute;ation</td>
          </tr>
          <tr>
            <td>Id&eacute;e de backlog</td>
            <td><code>I-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>M&ecirc;me (stable)</td>
            <td>Jamais renomm&eacute; apr&egrave;s cr&eacute;ation</td>
          </tr>
          <tr>
            <td>Tracer bullet</td>
            <td><code>TB-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>M&ecirc;me (stable)</td>
            <td>Jamais renomm&eacute; apr&egrave;s cr&eacute;ation</td>
          </tr>
          <tr>
            <td>Tranche de retrofit</td>
            <td><code>R-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>M&ecirc;me (stable)</td>
            <td>Jamais renomm&eacute; apr&egrave;s cr&eacute;ation</td>
          </tr>
          <tr>
            <td>Blitz &mdash; branche de fonctionnalit&eacute;</td>
            <td><code>_blitz-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td><code>blitz-archive/blitz-YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>Slug obligatoire&nbsp;; choisi au d&eacute;but de la session</td>
          </tr>
          <tr>
            <td>Blitz &mdash; main / branche unique</td>
            <td><code>_blitz-YYYY-MM-DD[-N].md</code></td>
            <td><code>blitz-archive/blitz-YYYY-MM-DD[-N].md</code></td>
            <td>Ordinal accept&eacute; si le th&egrave;me est flou au d&eacute;part</td>
          </tr>
          <tr>
            <td>Session grill</td>
            <td><code>&lt;sujet&gt;-grill-me.md</code></td>
            <td>M&ecirc;me</td>
            <td>Doit inclure un bloc de contr&ocirc;le &laquo;&nbsp;Resume at&nbsp;&raquo;</td>
          </tr>
          <tr>
            <td>D&eacute;cision de m&eacute;thodologie</td>
            <td><code>YYYY-MM-DD-&lt;slug&gt;.md</code></td>
            <td>M&ecirc;me</td>
            <td>Sous <code>methodology/decisions/</code></td>
          </tr>
          <tr>
            <td>Vue riche</td>
            <td><code>&lt;parent&gt;/view/index.html</code> <em>ou</em> <code>&lt;parent&gt;/view/&lt;slug&gt;/index.html</code></td>
            <td>M&ecirc;me</td>
            <td>HTML auto-contenu&nbsp;; li&eacute; depuis le parent Markdown</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <!-- SECTION 5 — RÉFÉRENCE DES COMPÉTENCES -->
  <section id="skills">
    <h2>R&eacute;f&eacute;rence des comp&eacute;tences</h2>
    <p class="section-intro">
      Invoquez les comp&eacute;tences par nom au d&eacute;but d&rsquo;une session. Voir les prompts de d&eacute;marrage
      rapide dans methodology/README.md.
    </p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Comp&eacute;tence</th>
            <th>Quand l&rsquo;invoquer</th>
            <th>Sortie principale</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>ezkey-vision-intake</code></td>
            <td>Nouvelle id&eacute;e brute entrant dans le syst&egrave;me</td>
            <td>Note de vision V-*</td>
          </tr>
          <tr>
            <td><code>ezkey-backlog-triage</code></td>
            <td>Apr&egrave;s vision-intake&nbsp;; classifier et prioriser</td>
            <td>Id&eacute;e de backlog I-* classifi&eacute;e</td>
          </tr>
          <tr>
            <td><code>ezkey-grill-me</code></td>
            <td>Avant le verrouillage de conception&nbsp;; tester les hypoth&egrave;ses</td>
            <td>Risques, options, questions critiques</td>
          </tr>
          <tr>
            <td><code>ezkey-plan-incubation</code></td>
            <td>Exploration libre avant la capture canonique</td>
            <td>Plan de travail &rarr; V-*/I-*/TB-*</td>
          </tr>
          <tr>
            <td><code>ezkey-tracer-bullet-promote</code></td>
            <td>Direction d&eacute;cid&eacute;e&nbsp;; pr&ecirc;t pour l&rsquo;ex&eacute;cution d&eacute;limit&eacute;e</td>
            <td>Brief de tracer bullet TB-*</td>
          </tr>
          <tr>
            <td><code>ezkey-component-design-pack</code></td>
            <td>Pr&eacute;paration de la conception par composant pour les fronti&egrave;res impact&eacute;es</td>
            <td>Briefs de conception, cartes de fronti&egrave;res, tables de d&eacute;cision</td>
          </tr>
          <tr>
            <td><code>ezkey-test-strategy-planner</code></td>
            <td>Avant l&rsquo;impl&eacute;mentation&nbsp;; s&eacute;lectionner les couches de test</td>
            <td>Tranche de plan de test</td>
          </tr>
          <tr>
            <td><code>ezkey-quality-gatekeeper</code></td>
            <td>Avant la cl&ocirc;ture&nbsp;; v&eacute;rifier les passerelles qualit&eacute;</td>
            <td>Rapport de passerelle</td>
          </tr>
          <tr>
            <td><code>ezkey-traceability-sync</code></td>
            <td>Cl&ocirc;ture&nbsp;; mettre &agrave; jour les r&eacute;f&eacute;rences crois&eacute;es et le statut</td>
            <td>R&eacute;f&eacute;rences d&rsquo;artefacts mises &agrave; jour</td>
          </tr>
          <tr>
            <td><code>ezkey-closeout</code></td>
            <td>Fin de session ou compl&eacute;tion de fonctionnalit&eacute;</td>
            <td>Transitions de statut, r&eacute;sum&eacute; de session</td>
          </tr>
          <tr>
            <td><code>ezkey-legacy-plan-miner</code></td>
            <td>Couloir retrofit&nbsp;: extraction des plans historiques et briefings verbaux</td>
            <td>Mat&eacute;riaux d&rsquo;entr&eacute;e R-*</td>
          </tr>
          <tr>
            <td><code>ezkey-retrofit-curator</code></td>
            <td>Apr&egrave;s extraction&nbsp;; produire des tranches de retrofit canoniques</td>
            <td>Tranches de retrofit R-*</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <!-- SECTION 6 — VUES RICHES -->
  <section id="rich-views">
    <h2>Vues riches</h2>
    <p class="section-intro">
      Compagnons HTML aux documents Markdown sources, cr&eacute;&eacute;s lorsque la richesse visuelle
      am&eacute;liore mat&eacute;riellement la clart&eacute; et l&rsquo;alignement conceptuel entre collaborateurs humains
      et agents IA. La base de la m&eacute;thodologie reste Markdown-first &mdash; les vues riches sont
      des suppl&eacute;ments d&eacute;lib&eacute;r&eacute;s, pas des remplacements.
    </p>

    <h3 class="sub">Quand cr&eacute;er une vue riche</h3>
    <ul class="criteria-list">
      <li><strong>Relations d&rsquo;entit&eacute;s complexes</strong> &mdash; plusieurs types d&rsquo;entit&eacute;s interd&eacute;pendants avec des interactions de cycle de vie difficiles &agrave; suivre en prose ou tableaux seuls.</li>
      <li><strong>Machines d&rsquo;&eacute;tat et gouvernance de cycle de vie</strong> &mdash; transitions d&rsquo;&eacute;tat, r&egrave;gles d&rsquo;activation/d&eacute;sactivation, propagation en cha&icirc;ne parent sur les types d&rsquo;entit&eacute;s.</li>
      <li><strong>Flux cryptographiques ou de protocole</strong> &mdash; flux multi-acteurs avec transit de mat&eacute;riel de cl&eacute;, fen&ecirc;tres de synchronisation et contraintes de s&eacute;quen&ccedil;age o&ugrave; un diagramme ancre la compr&eacute;hension.</li>
      <li><strong>D&eacute;cisions architecturales &agrave; fort impact</strong> &mdash; d&eacute;cisions transversales avec une complexit&eacute; visuelle qui b&eacute;n&eacute;ficie d&rsquo;un ancrage graphique durable.</li>
      <li><strong>La m&eacute;thodologie elle-m&ecirc;me</strong> &mdash; le processus et le syst&egrave;me d&rsquo;artefacts b&eacute;n&eacute;ficient d&rsquo;une r&eacute;f&eacute;rence visuelle accessible &agrave; la fois aux humains et aux agents IA au d&eacute;but d&rsquo;une session.</li>
    </ul>

    <h3 class="sub">Convention de dossier et de lien</h3>
    <div class="table-wrap" style="margin-bottom: 24px;">
      <table>
        <thead>
          <tr><th>Contexte</th><th>Chemin</th><th>Lien depuis le parent Markdown</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>M&eacute;thodologie (ce document)</td>
            <td><code>product-docs/methodology/view/index.html</code></td>
            <td><code>[Vue riche](view/index.html)</code></td>
          </tr>
          <tr>
            <td>Docs globaux (ex. Lifecycle Governance)</td>
            <td><code>docs/view/&lt;slug&gt;/index.html</code></td>
            <td><code>[Vue riche](view/&lt;slug&gt;/index.html)</code></td>
          </tr>
          <tr>
            <td>Niveau composant</td>
            <td><code>product-docs/components/&lt;comp&gt;/view/index.html</code></td>
            <td><code>[Vue riche](view/index.html)</code></td>
          </tr>
        </tbody>
      </table>
    </div>

    <h3 class="sub">Principes de conception</h3>
    <ul class="criteria-list teal">
      <li><strong>Auto-contenu</strong> &mdash; aucune d&eacute;pendance CDN externe. Int&eacute;grez tous les styles et SVG en ligne.</li>
      <li><strong>Palette sobre</strong> &mdash; l&rsquo;information d&rsquo;abord. La couleur est utilis&eacute;e pour la distinction, pas pour la d&eacute;coration.</li>
      <li><strong>Li&eacute; depuis le parent Markdown</strong> &mdash; le document source porte une r&eacute;f&eacute;rence claire &agrave; la vue riche.</li>
      <li><strong>Mis &agrave; jour dans le m&ecirc;me changeset</strong> &mdash; lorsque le document parent change, la vue riche est mise &agrave; jour avec lui.</li>
      <li><strong>Un seul <code>index.html</code></strong> par dossier de vue&nbsp;; d&rsquo;autres fichiers uniquement si la navigation apporte une valeur r&eacute;elle pour le sujet sp&eacute;cifique.</li>
    </ul>

  </section>

</main>
'@

# ---------------------------------------------------------------------------
# 9. Assemble and write French page
# ---------------------------------------------------------------------------
$frOutputPath = Join-Path $repoRoot 'sites\ezkey-org\fr\methodologie.html'

$frOutput = @"
<!DOCTYPE html>
<!-- Source: product-docs/methodology/view/index.html @ $syncDate -->
<!-- Langue: fr -->
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="description" content="R&eacute;f&eacute;rence visuelle pour le flux de bout en bout &mdash; de l&rsquo;id&eacute;e brute &agrave; la fonctionnalit&eacute; livr&eacute;e, avec les types d&rsquo;artefacts, les couloirs parall&egrave;les, les conventions de nommage et les comp&eacute;tences.">
  <meta property="og:title" content="M&eacute;thodologie Ezkey &mdash; Vue riche">
  <meta property="og:description" content="R&eacute;f&eacute;rence visuelle pour le flux de bout en bout &mdash; de l&rsquo;id&eacute;e brute &agrave; la fonctionnalit&eacute; livr&eacute;e.">
  <meta property="og:url" content="https://ezkey.org/fr/methodologie.html">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="Ezkey">
  <meta property="og:locale" content="fr_FR">
  <link rel="alternate" hreflang="en" href="https://ezkey.org/methodology.html">
  <link rel="alternate" hreflang="fr" href="https://ezkey.org/fr/methodologie.html">
  <link rel="alternate" hreflang="x-default" href="https://ezkey.org/methodology.html">
  <title>M&eacute;thodologie Ezkey &mdash; Vue riche</title>
  <style>
    /* -- Site shell -------------------------------------------------------- */
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      min-height: 100vh;
      color: #0f172a;
    }
    .shell {
      max-width: 1460px;
      margin: 0 auto;
      padding: 0 20px 56px;
    }
    .nav-top {
      padding: 14px 0 8px;
      font-size: 0.88rem;
      color: rgba(255,255,255,0.9);
    }
    .nav-top a { color: rgba(255,255,255,0.9); text-decoration: underline; }
    .nav-top a:hover { color: #fff; }
    .site-card {
      background: #fff;
      border-radius: 16px;
      box-shadow: 0 24px 60px rgba(0,0,0,0.18);
      overflow: hidden;
      margin-top: 8px;
    }
    @media (max-width: 640px) {
      .shell { padding: 0 12px 48px; }
      .site-card { border-radius: 12px; }
    }
    /* -- Canonical styles -------------------------------------------------- */
$styleInner
$paletteOverride
  </style>
</head>
<body>
  <div class="shell">
    <p class="nav-top">
      <a href="/fr/">Accueil (fran&ccedil;ais)</a>
      &middot; <a href="/methodology.html">English</a>
    </p>

$frNavBlock

    <div class="site-card">
$frHeaderBlock
$frMainBlock
$frFooterBlock
    </div>

    <footer class="site-footer" style="text-align:center;">
      Ezkey &nbsp;&middot;&nbsp; <a href="/fr/">ezkey.org</a>
    </footer>

  </div>
</body>
</html>
"@

if ($DryRun) {
    Write-Host "[DryRun] Would write $($frOutput.Length) chars to: $frOutputPath"
} else {
    [System.IO.File]::WriteAllText($frOutputPath, $frOutput, $utf8NoBom)
    Write-Host "Published FR: $frOutputPath"
    Write-Host "Sync:         Source: product-docs/methodology/view/index.html @ $syncDate"
    Write-Host "Next steps:   open sites/ezkey-org/fr/methodologie.html in a browser to validate."
}

<#
.SYNOPSIS
    Publishes the Ezkey Methodology rich view to the ezkey-org static site.
.DESCRIPTION
    Reads product-docs/methodology/view/index.html, extracts its visual blocks,
    applies the ezkey.org shell and palette, strips private Markdown links, and
    writes the public English and French pages:

      sites/ezkey-org/methodology.html
      sites/ezkey-org/fr/methodologie.html

    The French page is generated from the same canonical blocks through a
    controlled replacement map so both locales stay structurally aligned.
.PARAMETER DryRun
    When specified, prints what would be written but does not touch output files.
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
$outputPathEn  = Join-Path $repoRoot 'sites\ezkey-org\methodology.html'
$outputPathFr  = Join-Path $repoRoot 'sites\ezkey-org\fr\methodologie.html'
$syncDate      = Get-Date -Format 'yyyy-MM-dd'
$rx            = [System.Text.RegularExpressions.Regex]
$rxOpts        = [System.Text.RegularExpressions.RegexOptions]::Singleline
$utf8NoBom     = [System.Text.UTF8Encoding]::new($false)
$arrow         = [string][char]0x2192
$emDash        = [string][char]0x2014

function Get-RequiredMatch {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )

    $match = $rx::Match($Text, $Pattern, $rxOpts)
    if (-not $match.Success) {
        throw "Could not extract $Description from canonical source."
    }
    if ($match.Groups.Count -gt 1) {
        return $match.Groups[1].Value
    }
    return $match.Value
}

function Remove-PrivateMarkdownLinks {
    param([string]$Html)

    return $Html -replace '(?s)<a href="[^"]*\.md[^"]*"[^>]*>(.*?)</a>', '$1'
}

function Apply-Replacements {
    param(
        [string]$Text,
        [object[]]$Pairs
    )

    $result = $Text
  if ($Pairs.Count -eq 0) {
    return $result
  }

  $pendingOld = $null
  foreach ($entry in $Pairs) {
    $isNestedPair = (
      (($entry -is [array]) -or ($entry -is [System.Collections.IList])) -and
      -not ($entry -is [string]) -and
      $entry.Count -ge 2)

    if ($isNestedPair) {
      $result = $result.Replace([string]$entry[0], [string]$entry[1])
      continue
    }

    if ($null -eq $pendingOld) {
      $pendingOld = [string]$entry
    } else {
      $result = $result.Replace($pendingOld, [string]$entry)
      $pendingOld = $null
    }
  }

    return $result
}

function Write-Page {
    param(
        [string]$Path,
        [string]$Content,
        [string]$Label
    )

    $normalized = ($Content -replace "`r`n", "`n").TrimEnd("`r", "`n") + "`n"
    if ($DryRun) {
        Write-Host "[DryRun] Would write $($normalized.Length) chars to: $Path"
    } else {
        [System.IO.File]::WriteAllText($Path, $normalized, $utf8NoBom)
        Write-Host "Published ${Label}: $Path"
    }
}

Write-Host "Reading canonical:  $canonicalPath"
$src = [System.IO.File]::ReadAllText($canonicalPath, $utf8NoBom)

$styleInner  = Get-RequiredMatch $src '(?s)<style>(.*?)</style>' '<style> block'
$navBlock    = Get-RequiredMatch $src '(?s)<nav>.*?</nav>' '<nav> block'
$headerBlock = Get-RequiredMatch $src '(?s)<header class="page-header">.*?</header>' '<header class="page-header"> block'
$mainBlock   = Get-RequiredMatch $src '(?s)<main>.*?</main>' '<main> block'
$footerBlock = Get-RequiredMatch $src '(?s)<footer>.*?</footer>' '<footer> block'

$navBlock    = Remove-PrivateMarkdownLinks $navBlock
$headerBlock = Remove-PrivateMarkdownLinks $headerBlock
$mainBlock   = Remove-PrivateMarkdownLinks $mainBlock
$footerBlock = Remove-PrivateMarkdownLinks $footerBlock

$mainBlock = $rx::Replace(
    $mainBlock,
    '(?s)\s*<!--\s*pub-exclude-start\s*-->.*?<!--\s*pub-exclude-end\s*-->',
    '',
    $rxOpts)

$headerBlock = $headerBlock.Replace(
  '<span>Source: product-docs/methodology/README.md</span>',
  '<span>Source: Ezkey methodology corpus</span>')
$footerBlock = $footerBlock.Replace(
  'Source: product-docs/methodology/README.md',
  'Source: Ezkey methodology corpus')
$mainBlock = $mainBlock.Replace(
  'The canonical values live in methodological-values.md.',
  'The canonical values live in the Ezkey methodology corpus.')

$paletteOverride = @'
    /* Site palette overrides */
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

$siteShellCss = @'
    /* Site shell */
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
'@

$descriptionEn = 'Visual reference for the ideation-to-delivery workflow, methodological values, artifact types, state boundaries, traceability, and skills.'
$descriptionFr = 'R&eacute;f&eacute;rence visuelle pour le flux de l''id&eacute;ation &agrave; la livraison, les valeurs m&eacute;thodologiques, les types d''artefacts, les fronti&egrave;res d''&eacute;tat, la tra&ccedil;abilit&eacute; et les comp&eacute;tences.'

# Cross-surface bridge: bring the visitor of the rich view to the living explorer.
# Top CTA appears between the page header and the main content; bottom reminder
# appears between the main content and the rich-view footer. Both open in a new
# tab to signal an origin switch (ezkey.org -> methodology.ezkey.org).
$explorerCtaEn = @'

<aside class="explorer-cta" style="margin:24px 32px;padding:20px 24px;border:1px solid #cbd5e1;border-radius:10px;background:linear-gradient(135deg,#f1f5f9 0%,#e0e7ff 100%);display:flex;flex-direction:column;gap:10px;">
  <div style="font-size:1.05rem;font-weight:700;color:#0f172a;">Explore the living methodology</div>
  <p style="margin:0;color:#334155;font-size:0.95rem;line-height:1.55;">
    This page is the panorama. The living version is published as a separate site, updated continuously from the source repository as the methodology evolves.
  </p>
  <div>
    <a href="https://methodology.ezkey.org" target="_blank" rel="noopener" style="display:inline-flex;align-items:center;gap:6px;margin-top:2px;padding:10px 18px;background:#2563eb;color:#fff;border-radius:6px;text-decoration:none;font-weight:600;font-size:0.92rem;">
      Open the methodology explorer
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 17L17 7"/><path d="M7 7h10v10"/></svg>
    </a>
  </div>
  <div style="font-size:0.82rem;color:#64748b;">methodology.ezkey.org &middot; every artifact, fully searchable &middot; updated continuously &middot; English</div>
</aside>
'@

$explorerReminderEn = @'

<p style="margin:8px 32px 24px;text-align:center;font-size:0.9rem;color:#475569;">
  Looking for the searchable, always-current reference?
  <a href="https://methodology.ezkey.org" target="_blank" rel="noopener" style="color:#2563eb;font-weight:600;text-decoration:none;">Open the methodology explorer &#8599;</a>
</p>
'@

$explorerCtaFr = @'

<aside class="explorer-cta" style="margin:24px 32px;padding:20px 24px;border:1px solid #cbd5e1;border-radius:10px;background:linear-gradient(135deg,#f1f5f9 0%,#e0e7ff 100%);display:flex;flex-direction:column;gap:10px;">
  <div style="font-size:1.05rem;font-weight:700;color:#0f172a;">Explorer la m&eacute;thodologie vivante</div>
  <p style="margin:0;color:#334155;font-size:0.95rem;line-height:1.55;">
    Cette page en est le panorama. La version vivante est publi&eacute;e comme un site distinct, mis &agrave; jour en continu depuis le d&eacute;p&ocirc;t source au fil de l&rsquo;&eacute;volution de la m&eacute;thodologie.
  </p>
  <div>
    <a href="https://methodology.ezkey.org" target="_blank" rel="noopener" style="display:inline-flex;align-items:center;gap:6px;margin-top:2px;padding:10px 18px;background:#2563eb;color:#fff;border-radius:6px;text-decoration:none;font-weight:600;font-size:0.92rem;">
      Ouvrir l&rsquo;explorateur de la m&eacute;thodologie
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 17L17 7"/><path d="M7 7h10v10"/></svg>
    </a>
  </div>
  <div style="font-size:0.82rem;color:#64748b;">methodology.ezkey.org &middot; chaque artefact, enti&egrave;rement consultable &middot; mis &agrave; jour en continu &middot; en anglais</div>
</aside>
'@

$explorerReminderFr = @'

<p style="margin:8px 32px 24px;text-align:center;font-size:0.9rem;color:#475569;">
  Vous cherchez la r&eacute;f&eacute;rence consultable et toujours &agrave; jour&nbsp;?
  <a href="https://methodology.ezkey.org" target="_blank" rel="noopener" style="color:#2563eb;font-weight:600;text-decoration:none;">Ouvrir l&rsquo;explorateur de la m&eacute;thodologie &#8599;</a>
</p>
'@

$outputEn = @"
<!DOCTYPE html>
<!-- Source: product-docs/methodology/view/index.html @ $syncDate -->
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="description" content="$descriptionEn">
  <meta property="og:title" content="Ezkey Methodology &mdash; Rich View">
  <meta property="og:description" content="$descriptionEn">
  <meta property="og:url" content="https://ezkey.org/methodology.html">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="Ezkey">
  <meta property="og:locale" content="en_US">
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="Ezkey Methodology &mdash; Rich View">
  <meta name="twitter:description" content="$descriptionEn">
  <meta name="twitter:image" content="https://ezkey.org/logo.svg">
  <link rel="alternate" hreflang="en" href="https://ezkey.org/methodology.html">
  <link rel="alternate" hreflang="fr" href="https://ezkey.org/fr/methodologie.html">
  <link rel="alternate" hreflang="x-default" href="https://ezkey.org/methodology.html">
  <title>Ezkey Methodology &mdash; Rich View</title>
  <style>
$siteShellCss
    /* Canonical styles (from product-docs/methodology/view/index.html) */
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
$explorerCtaEn
$mainBlock
$explorerReminderEn
$footerBlock
    </div>

    <footer class="site-footer" style="text-align:center;">
      Ezkey &nbsp;&middot;&nbsp; <a href="/">ezkey.org</a>
    </footer>
  </div>
</body>
</html>
"@

$frPairs = @(
    @('Ezkey <span>Methodology</span>', 'Ezkey <span>M&eacute;thodologie</span>'),
  @('Methodological Values', 'Valeurs m&eacute;thodologiques'),
  @('End-to-End Workflow', 'Flux de travail de bout en bout'),
  @('Artifact Types', 'Types d&rsquo;artefacts'),
  @('Parallel Lanes', 'Couloirs parall&egrave;les'),
  @('Collaboration Context', 'Contexte de collaboration'),
  @('Naming Conventions', 'Conventions de nommage'),
  @('Bidirectional Traceability', 'Tra&ccedil;abilit&eacute; bidirectionnelle'),
  @('Skill Boundary Contracts', 'Contrats de fronti&egrave;res des comp&eacute;tences'),
    @('Workflow', 'Flux'),
    @('Values', 'Valeurs'),
    @('Artifacts', 'Artefacts'),
    @('Lanes', 'Couloirs'),
  @('Naming', 'Nommage'),
    @('Traceability', 'Tra&ccedil;abilit&eacute;'),
    @('Skills', 'Comp&eacute;tences'),
    @('Rich Views', 'Vues riches'),
    @('<h1>Ezkey Methodology</h1>', '<h1>M&eacute;thodologie Ezkey</h1>'),
    @('Visual reference for the ideation-to-delivery workflow &mdash; from raw idea to closed feature, with methodological values, artifact types, state boundaries, traceability, and skills.', 'R&eacute;f&eacute;rence visuelle pour le flux de l&rsquo;id&eacute;ation &agrave; la livraison &mdash; de l&rsquo;id&eacute;e brute &agrave; la tranche cl&ocirc;tur&eacute;e, avec les valeurs m&eacute;thodologiques, les types d&rsquo;artefacts, les fronti&egrave;res d&rsquo;&eacute;tat, la tra&ccedil;abilit&eacute; et les comp&eacute;tences.'),
    @('Source: Ezkey methodology corpus', 'Source&nbsp;: corpus m&eacute;thodologique Ezkey'),
    @('Source:', 'Source&nbsp;:'),
    @('Decision:', 'D&eacute;cision&nbsp;:'),
    @('Updated 2026-05-25', 'Mis &agrave; jour le 2026-05-25'),
    @('The canonical values live in the Ezkey methodology corpus. This view keeps the operating tests visible: enough rigor to preserve judgment and traceability, but not enough ceremony to slow simple work.', 'Les valeurs canoniques vivent dans le corpus m&eacute;thodologique Ezkey. Cette vue garde les tests op&eacute;ratoires visibles&nbsp;: assez de rigueur pour pr&eacute;server le jugement et la tra&ccedil;abilit&eacute;, sans assez de c&eacute;r&eacute;monie pour ralentir le travail simple.'),
    @('Proportional rigor', 'Rigueur proportionnelle'),
    @('Process weight follows uncertainty and risk, not habit or artifact size.', 'Le poids du processus suit l&rsquo;incertitude et le risque, pas l&rsquo;habitude ni la taille de l&rsquo;artefact.'),
    @('Internal integrity', 'Int&eacute;grit&eacute; interne'),
    @('Lanes, skills, statuses, and artifacts connect without hidden session memory.', 'Les couloirs, comp&eacute;tences, statuts et artefacts se relient sans m&eacute;moire cach&eacute;e de session.'),
    @('Couloirs, skills, statuses, and artifacts connect without hidden session memory.', 'Les couloirs, comp&eacute;tences, statuts et artefacts se relient sans m&eacute;moire cach&eacute;e de session.'),
    @('Explicit uncertainty', 'Incertitude explicite'),
    @('Statuses name what is known, what is open, and what confidence the artifact supports.', 'Les statuts nomment ce qui est connu, ce qui reste ouvert et le niveau de confiance que l&rsquo;artefact supporte.'),
    @('Readable state boundaries', 'Fronti&egrave;res d&rsquo;&eacute;tat lisibles'),
    @('Every skill has entry, exit, next-step, and fast-path boundaries.', 'Chaque comp&eacute;tence a des fronti&egrave;res d&rsquo;entr&eacute;e, de sortie, d&rsquo;&eacute;tape suivante et de chemin rapide.'),
    @('Bidirectional traceability', 'Tra&ccedil;abilit&eacute; bidirectionnelle'),
    @('Forward links show where work goes; backward links prove why it exists.', 'Les liens vers l&rsquo;avant montrent o&ugrave; va le travail; les liens arri&egrave;re prouvent pourquoi il existe.'),
    @('Earned permanence', 'Permanence m&eacute;rit&eacute;e'),
    @('Living documents, registries, checks, and skills must earn their maintenance cost.', 'Les documents vivants, registres, contr&ocirc;les et comp&eacute;tences doivent m&eacute;riter leur co&ucirc;t de maintenance.'),
    @('Test:', 'Test&nbsp;:'),
    @('what concrete risk does this extra step reduce?', 'quel risque concret cette &eacute;tape suppl&eacute;mentaire r&eacute;duit-elle?'),
    @('can a later human or agent resume from here?', 'un humain ou un agent ult&eacute;rieur peut-il reprendre d&rsquo;ici?'),
    @('does this output expose uncertainty instead of smoothing it over?', 'cette sortie expose-t-elle l&rsquo;incertitude au lieu de la lisser?'),
    @('is the transition clear to a tired human and a cold agent?', 'la transition est-elle claire pour un humain fatigu&eacute; et un agent froid?'),
    @('can we navigate both to the source and to the durable outcome?', 'peut-on naviguer &agrave; la fois vers la source et vers le r&eacute;sultat durable?'),
    @('who updates this, when, and what if it stays stale?', 'qui met cela &agrave; jour, quand, et que faire si cela reste p&eacute;rim&eacute;?'),
    @('Lane A', 'Couloir A'),
    @('Nine explicit phases with artifact outputs and exit criteria. Move to implementation as soon as direction, first cut, validation criteria, and non-blocking open questions are all settled &mdash; avoiding both premature coding and perpetual preparation.', 'Neuf phases explicites avec sorties d&rsquo;artefacts et crit&egrave;res de sortie. Passez &agrave; l&rsquo;impl&eacute;mentation d&egrave;s que la direction, la premi&egrave;re coupe, les crit&egrave;res de validation et les questions ouvertes non bloquantes sont &eacute;tablis &mdash; en &eacute;vitant autant le code pr&eacute;matur&eacute; que la pr&eacute;paration perp&eacute;tuelle.'),
    @('Promote', 'Promouvoir'),
    @('Analyze &amp; Design', 'Analyser &amp; concevoir'),
    @('Plan Tests', 'Planifier les tests'),
    @('Gate', 'Passerelle'),
    @('Implement', 'Impl&eacute;menter'),
    @('Close Out', 'Cl&ocirc;turer'),
    @('Record the raw idea with clear intent and initial tags. Keep the first version short and expressive.', 'Enregistrez l&rsquo;id&eacute;e brute avec une intention claire et des tags initiaux. Gardez la premi&egrave;re version courte et expressive.'),
    @('Clarify intent, user value, risk, and rough scope. Assign status, priority, phase tags, component tags.', 'Clarifiez l&rsquo;intention, la valeur utilisateur, le risque et la port&eacute;e approximative. Assignez le statut, la priorit&eacute;, les tags de phase et de composant.'),
    @('Structured questioning pass (&quot;Grill Me&quot;). Surface assumptions, exceptions, error paths, non-goals.', 'Passe de questionnement structur&eacute; (&laquo;&nbsp;Grill Me&nbsp;&raquo;). Faites &eacute;merger les hypoth&egrave;ses, exceptions, chemins d&rsquo;erreur et non-objectifs.'),
    @('Structured questioning pass ("Grill Me"). Surface assumptions, exceptions, error paths, non-goals.', 'Passe de questionnement structur&eacute; (&laquo;&nbsp;Grill Me&nbsp;&raquo;). Faites &eacute;merger les hypoth&egrave;ses, exceptions, chemins d&rsquo;erreur et non-objectifs.'),
    @('Define the first vertical slice and expected evidence. Avoid adding preparation if direction is already clear.', 'D&eacute;finissez la premi&egrave;re tranche verticale et les preuves attendues. &Eacute;vitez d&rsquo;ajouter de la pr&eacute;paration si la direction est d&eacute;j&agrave; claire.'),
    @('Global analysis + component-specific analysis for each impacted boundary. Define responsibilities, contracts, validation, error behavior.', 'Analyse globale et analyse par composant pour chaque fronti&egrave;re impact&eacute;e. D&eacute;finissez les responsabilit&eacute;s, contrats, validations et comportements d&rsquo;erreur.'),
    @('Select minimum and optional test layers: unit, functional, elective, operational, UI. Keep selection risk-based and cost-aware.', 'S&eacute;lectionnez les couches de test minimales et optionnelles&nbsp;: unitaire, fonctionnel, &eacute;lectif, op&eacute;rationnel, UI. Gardez la s&eacute;lection bas&eacute;e sur les risques et les co&ucirc;ts.'),
    @('Apply methodology fit, analysis, design, implementation, and closeout gates. Verify mandatory checks before implementation.', 'Appliquez les passerelles d&rsquo;ad&eacute;quation m&eacute;thodologique, d&rsquo;analyse, de conception, d&rsquo;impl&eacute;mentation et de cl&ocirc;ture. V&eacute;rifiez les contr&ocirc;les obligatoires avant l&rsquo;impl&eacute;mentation.'),
    @('Execute the plan in code and tests. Update docs and contracts in the same changeset. Controller changes imply contract review.', 'Ex&eacute;cutez le plan en code et en tests. Mettez &agrave; jour les docs et contrats dans le m&ecirc;me changeset. Toute modification de contr&ocirc;leur implique une revue de contrat.'),
    @('Transition status, record evidence, separate corpus closure from active product backlog, and name residual risk.', 'Faites transiter les statuts, consignez les preuves, s&eacute;parez la cl&ocirc;ture du corpus du backlog produit actif et nommez le risque r&eacute;siduel.'),
    @('Done when:', 'Termin&eacute; quand&nbsp;:'),
    @('clear intent and tags assigned.', 'intention claire et tags assign&eacute;s.'),
    @('scope and value understandable.', 'port&eacute;e et valeur compr&eacute;hensibles.'),
    @('key risks and exceptions explicit.', 'risques cl&eacute;s et exceptions explicites.'),
    @('one vertical slice is testable.', 'une tranche verticale est testable.'),
    @('boundaries and decision points explicit.', 'fronti&egrave;res et points de d&eacute;cision explicites.'),
    @('min test layers explicitly selected.', 'couches de test minimales explicitement s&eacute;lectionn&eacute;es.'),
    @('mandatory quality checks pass.', 'les contr&ocirc;les qualit&eacute; obligatoires passent.'),
    @('evidence complete and gates green.', 'preuves compl&egrave;tes et passerelles vertes.'),
    @('closure is honest and resumable.', 'cl&ocirc;ture honn&ecirc;te et reprenable.'),
    @('I-* updated', 'I-* mis &agrave; jour'),
    @('I-* + open Qs', 'I-* + questions ouvertes'),
    @('test plan slice', 'tranche de plan de test'),
    @('gate report', 'rapport de passerelle'),
    @('status + traceability', 'statut + tra&ccedil;abilit&eacute;'),
    @('All canonical artifacts use date+slug identifiers. No global counter lookup required. Identifiers are stable &mdash; never renamed after creation, never reused.', 'Tous les artefacts canoniques utilisent des identifiants date+slug. Aucun compteur global n&rsquo;est requis. Les identifiants sont stables &mdash; jamais renomm&eacute;s apr&egrave;s cr&eacute;ation, jamais r&eacute;utilis&eacute;s.'),
    @('Identifier format', 'Format d&rsquo;identifiant'),
    @('Location', 'Emplacement'),
    @('Created at', 'Cr&eacute;&eacute; lors de'),
    @('Status lifecycle', 'Cycle de vie du statut'),
    @('Vision note', 'Note de vision'),
    @('Backlog idea', 'Id&eacute;e de backlog'),
    @('Retrofit slice', 'Tranche de retrofit'),
    @('Working plan', 'Plan de travail'),
    @('Blitz scratch', 'Brouillon blitz'),
    @('Grill session', 'Session grill'),
    @('Challenge', 'Questionnement'),
    @('Methodology decision', 'D&eacute;cision de m&eacute;thodologie'),
    @('Rich view', 'Vue riche'),
    @('folder slug', 'slug de dossier'),
    @('Retrofit lane', 'Couloir retrofit'),
    @('Plan incubation lane', 'Couloir incubation de plan'),
    @('Non-canonical &mdash; materialized into V-*/I-*/TB-*', 'Non canonique &mdash; mat&eacute;rialis&eacute; en V-*/I-*/TB-*'),
    @('Active (<code>_</code> prefix) &rarr; archived (no prefix, in <code>blitz-archive/</code>)', 'Actif (pr&eacute;fixe <code>_</code>) &rarr; archiv&eacute; (sans pr&eacute;fixe, dans <code>blitz-archive/</code>)'),
    @('open &rarr; resumed &rarr; complete, with post-decision notes when superseded', 'ouverte &rarr; reprise &rarr; compl&egrave;te, avec notes post-d&eacute;cision quand elle est remplac&eacute;e'),
    @('Any &mdash; when a non-obvious methodology rule is adopted', 'N&rsquo;importe quand &mdash; quand une r&egrave;gle m&eacute;thodologique non &eacute;vidente est adopt&eacute;e'),
    @('Stable &mdash; never revised in place (supersede with new file)', 'Stable &mdash; jamais r&eacute;vis&eacute;e en place (remplacer avec un nouveau fichier)'),
    @('Any &mdash; when visual richness materially adds clarity', 'N&rsquo;importe quand &mdash; quand la richesse visuelle ajoute mat&eacute;riellement de la clart&eacute;'),
    @('Companion to its Markdown parent &mdash; updated with it', 'Compagnon de son parent Markdown &mdash; mis &agrave; jour avec lui'),
    @('Two lanes run alongside the main ideation-to-delivery flow. Any session may combine Lane A with one or both parallel lanes.', 'Deux couloirs s&rsquo;ex&eacute;cutent en parall&egrave;le du flux principal d&rsquo;id&eacute;ation &agrave; livraison. Une session peut combiner le couloir A avec l&rsquo;un ou les deux couloirs parall&egrave;les.'),
    @('Lane B', 'Couloir B'),
    @('Lane C', 'Couloir C'),
    @('Plan Incubation', 'Incubation de plan'),
    @('Legacy Retrofit', 'Retrofit historique'),
    @('Create or evolve a working plan in <code>plans/</code>', 'Cr&eacute;er ou faire &eacute;voluer un plan de travail dans <code>plans/</code>'),
    @('Explore options and converge on direction (freeform)', 'Explorer les options et converger vers une direction (forme libre)'),
    @('Materialize durable signal into V-*/I-*/TB-*', 'Mat&eacute;rialiser le signal durable en V-*/I-*/TB-*'),
    @('Cross-link plan and canonical artifacts when useful', 'Relier le plan et les artefacts canoniques lorsque c&rsquo;est utile'),
    @('Do not treat as retrofit unless genuinely historical', 'Ne pas traiter comme du retrofit sauf si la source est vraiment historique'),
    @('Select a small source batch (historical plans, verbal history)', 'S&eacute;lectionner un petit lot source (plans historiques, historique verbal)'),
    @('Extract decisions, invariants, patterns, test signal', 'Extraire les d&eacute;cisions, invariants, patterns et signaux de test'),
    @('Map into canonical docs', 'Mapper dans les docs canoniques'),
    @('Record a retrofit slice (R-*)', 'Enregistrer une tranche de retrofit (R-*)'),
    @('Close with residual gaps and next action', 'Cl&ocirc;turer avec les lacunes r&eacute;siduelles et l&rsquo;action suivante'),
    @('Not a lane &mdash; a cross-cutting modifier. These rules apply on top of Lanes A, B, and C whenever collaboration happens across parallel Git branches or worktrees. Sessions that are single-branch do not need to apply them.', 'Pas un couloir &mdash; un modificateur transversal. Ces r&egrave;gles s&rsquo;appliquent en plus des couloirs A, B et C quand la collaboration se fait sur des branches Git parall&egrave;les ou des worktrees. Les sessions &agrave; branche unique n&rsquo;ont pas besoin de les appliquer.'),
    @('Cross-cutting &middot; applies to Lanes A, B, C', 'Transversal &middot; s&rsquo;applique aux couloirs A, B, C'),
    @('When two developers (or one developer across two worktrees) work simultaneously, classic ordinal naming creates merge-time collisions. The collaboration context resolves this without coordination overhead, by making artifact names self-sufficient through date+slug identifiers and deferring shared indexes to post-merge.', 'Quand deux d&eacute;veloppeurs (ou un d&eacute;veloppeur sur deux worktrees) travaillent simultan&eacute;ment, le nommage ordinal classique cr&eacute;e des collisions &agrave; la fusion. Le contexte de collaboration r&eacute;sout cela sans surcharge de coordination, en rendant les noms d&rsquo;artefacts autosuffisants par les identifiants date+slug et en diff&eacute;rant les index partag&eacute;s apr&egrave;s la fusion.'),
    @('Date+slug IDs mandatory', 'IDs date+slug obligatoires'),
    @('No counter lookup, no contention across branches.', 'Aucune recherche de compteur, aucune contention entre branches.'),
    @('Create artifacts freely on branch', 'Cr&eacute;er les artefacts librement sur la branche'),
    @('V-*, I-*, TB-*, R-* &mdash; no cross-branch coordination needed.', 'V-*, I-*, TB-*, R-* &mdash; aucune coordination inter-branches n&eacute;cessaire.'),
    @('Blitz slug required', 'Slug blitz obligatoire'),
    @('On a feature branch, topic slug is chosen at session start &mdash; not the ordinal fallback.', 'Sur une branche de fonctionnalit&eacute;, le slug de sujet est choisi au d&eacute;but de la session &mdash; pas le repli ordinal.'),
    @('Defer index updates', 'Diff&eacute;rer les mises &agrave; jour d&rsquo;index'),
    @('update post-merge on <code>main</code>.', 'mettre &agrave; jour apr&egrave;s la fusion sur <code>main</code>.'),
    @('Index reconciliation at merge', 'R&eacute;conciliation d&rsquo;index &agrave; la fusion'),
    @('Run a reconciliation pass when the branch lands on <code>main</code>.', 'Ex&eacute;cuter une passe de r&eacute;conciliation quand la branche arrive sur <code>main</code>.'),
    @('Stable, collision-resistant identifiers across all branches and worktrees. Legacy <code>NNNN</code> artifacts are never renamed.', 'Identifiants stables et r&eacute;sistants aux collisions sur toutes les branches et worktrees. Les artefacts h&eacute;rit&eacute;s <code>NNNN</code> ne sont jamais renomm&eacute;s.'),
    @('Artifact', 'Artefact'),
    @('Active filename', 'Nom de fichier actif'),
    @('Archived / final', 'Archiv&eacute; / final'),
    @('Key rule', 'R&egrave;gle cl&eacute;'),
    @('Same (stable)', 'M&ecirc;me (stable)'),
    @('Never renamed after creation', 'Jamais renomm&eacute; apr&egrave;s cr&eacute;ation'),
    @('Blitz &mdash; feature branch', 'Blitz &mdash; branche de fonctionnalit&eacute;'),
    @('Blitz &mdash; main / single-branch', 'Blitz &mdash; main / branche unique'),
    @('Slug required; chosen at session start', 'Slug obligatoire; choisi au d&eacute;but de la session'),
    @('Ordinal accepted if theme unclear at start', 'Ordinal accept&eacute; si le th&egrave;me est flou au d&eacute;part'),
    @('Must include &quot;Resume at&quot; control block', 'Doit inclure le bloc de contr&ocirc;le &laquo;&nbsp;Resume at&nbsp;&raquo;'),
    @('Under <code>methodology/decisions/</code>', 'Sous <code>methodology/decisions/</code>'),
    @('Self-contained HTML; linked from parent Markdown', 'HTML autonome; li&eacute; depuis le parent Markdown'),
    @('The workflow is not only a forward pipeline. Closeout and audit must also prove where an artifact came from, what canon absorbed it, and whether later decisions changed its meaning.', 'Le flux n&rsquo;est pas seulement un pipeline vers l&rsquo;avant. La cl&ocirc;ture et l&rsquo;audit doivent aussi prouver d&rsquo;o&ugrave; vient un artefact, quel canon l&rsquo;a absorb&eacute; et si des d&eacute;cisions ult&eacute;rieures ont chang&eacute; son sens.'),
    @('Raw idea, blitz archive, working plan, historical plan, or verbal briefing.', 'Id&eacute;e brute, archive blitz, plan de travail, plan historique ou briefing verbal.'),
    @('Materialization', 'Mat&eacute;rialisation'),
    @('<code>V-*</code>, <code>I-*</code>, <code>R-*</code>, or documented drop with rationale.', '<code>V-*</code>, <code>I-*</code>, <code>R-*</code> ou abandon document&eacute; avec justification.'),
    @('Grill session, open questions, decision pressure points, status alignment.', 'Session grill, questions ouvertes, points de pression d&eacute;cisionnels, alignement des statuts.'),
    @('Canon', 'Canon'),
    @('ADR, methodology decision, policy output, component pack, or traceability matrix.', 'ADR, d&eacute;cision m&eacute;thodologique, sortie de politique, pack composant ou matrice de tra&ccedil;abilit&eacute;.'),
    @('Closeout', 'Cl&ocirc;ture'),
    @('Status transition, evidence, residual risk, next action, or supersession.', 'Transition de statut, preuve, risque r&eacute;siduel, action suivante ou supersession.'),
    @('Backward audit', 'Audit arri&egrave;re'),
    @('Can this closeout navigate back to the source and materialized artifacts?', 'Cette cl&ocirc;ture permet-elle de revenir &agrave; la source et aux artefacts mat&eacute;rialis&eacute;s?'),
    @('Status alignment', 'Alignement des statuts'),
    @('Do indexes and artifact metadata agree after grill, retrofit, or implementation?', 'Les index et m&eacute;tadonn&eacute;es d&rsquo;artefacts concordent-ils apr&egrave;s grill, retrofit ou impl&eacute;mentation?'),
    @('Current truth', 'V&eacute;rit&eacute; courante'),
    @('Are post-decision notes and supersession links present when history changed?', 'Les notes post-d&eacute;cision et liens de supersession sont-ils pr&eacute;sents quand l&rsquo;historique a chang&eacute;?'),
    @('Honest closure', 'Cl&ocirc;ture honn&ecirc;te'),
    @('Is corpus integration separated from still-active product backlog work?', 'L&rsquo;int&eacute;gration corpus est-elle s&eacute;par&eacute;e du backlog produit encore actif?'),
    @('Each skill answers the same four questions: enter when, exit when, call next, and not needed when. The canonical skill text lives under <code>.cursor/skills/</code>; this table is the fast navigation surface.', 'Chaque comp&eacute;tence r&eacute;pond aux quatre m&ecirc;mes questions&nbsp;: entrer quand, sortir quand, appeler ensuite et inutile quand. Le texte canonique vit sous <code>.cursor/skills/</code>; ce tableau est la surface de navigation rapide.'),
    @('Skill', 'Comp&eacute;tence'),
    @('Enter when', 'Entrer quand'),
    @('Exit when', 'Sortir quand'),
    @('Call next', 'Appeler ensuite'),
    @('Not needed when', 'Inutile quand'),
    @('Strategic direction, product intent, or early feature ideas arrive.', 'Une direction strat&eacute;gique, une intention produit ou des id&eacute;es de fonctionnalit&eacute; arrivent.'),
    @('Durable direction is captured as <code>V-*</code> or initial <code>I-*</code> with next step.', 'La direction durable est captur&eacute;e en <code>V-*</code> ou <code>I-*</code> initial avec prochaine &eacute;tape.'),
    @('The idea is already a bounded implementation slice.', 'L&rsquo;id&eacute;e est d&eacute;j&agrave; une tranche d&rsquo;impl&eacute;mentation born&eacute;e.'),
    @('An <code>I-*</code> exists or a vision note has actionable scope.', 'Un <code>I-*</code> existe ou une note de vision a une port&eacute;e actionnable.'),
    @('Scope, non-scope, priority, status, tags, risks, and posture are explicit.', 'Port&eacute;e, hors-port&eacute;e, priorit&eacute;, statut, tags, risques et posture sont explicites.'),
    @('The item is still pure orientation or already fully scoped.', 'L&rsquo;item est encore une pure orientation ou d&eacute;j&agrave; enti&egrave;rement cadr&eacute;.'),
    @('Assumptions, exception paths, lifecycle effects, or boundary risks need pressure.', 'Les hypoth&egrave;ses, chemins d&rsquo;exception, effets de cycle de vie ou risques de fronti&egrave;re doivent &ecirc;tre mis sous pression.'),
    @('Risks, open questions, decision pressure points, and clarifications are explicit.', 'Risques, questions ouvertes, points de pression d&eacute;cisionnels et clarifications sont explicites.'),
    @('Triage, tracer-bullet promotion, or component design.', 'Triage, promotion tracer bullet ou design composant.'),
    @('The change is low-risk, bounded, and has known validation criteria.', 'Le changement est &agrave; faible risque, born&eacute; et a des crit&egrave;res de validation connus.'),
    @('The operator starts from a current-session working plan.', 'L&rsquo;op&eacute;rateur part d&rsquo;un plan de travail de session courante.'),
    @('Durable signal is classified for <code>V-*</code>, <code>I-*</code>, <code>TB-*</code>, principle adoption, or mix.', 'Le signal durable est class&eacute; pour <code>V-*</code>, <code>I-*</code>, <code>TB-*</code>, adoption de principe ou combinaison.'),
    @('Vision intake, backlog triage, or tracer-bullet promotion.', 'Intake de vision, triage backlog ou promotion tracer bullet.'),
    @('The source is historical retrofit input or a direct implementation task.', 'La source est une entr&eacute;e de retrofit historique ou une t&acirc;che directe d&rsquo;impl&eacute;mentation.'),
    @('An <code>I-*</code> is <code>ready</code> and needs a bounded vertical slice.', 'Un <code>I-*</code> est <code>ready</code> et a besoin d&rsquo;une tranche verticale born&eacute;e.'),
    @('The <code>TB-*</code> has posture, scope, boundaries, exclusions, and evidence criteria.', 'Le <code>TB-*</code> a une posture, une port&eacute;e, des fronti&egrave;res, des exclusions et des crit&egrave;res de preuve.'),
    @('The item lacks direction, or the change is a tiny code/doc fix.', 'L&rsquo;item manque de direction, ou le changement est une petite correction code/doc.'),
    @('A scope touches component boundaries, mappings, validations, or error behavior.', 'Une port&eacute;e touche les fronti&egrave;res composant, mappings, validations ou comportements d&rsquo;erreur.'),
    @('Impacted components have responsibilities, contracts, tests, and doc impact named.', 'Les composants impact&eacute;s ont responsabilit&eacute;s, contrats, tests et impacts docs nomm&eacute;s.'),
    @('The change is local, internal, and not observable at a boundary.', 'Le changement est local, interne et non observable &agrave; une fronti&egrave;re.'),
    @('A change has known risks and needs explicit test-layer selection.', 'Un changement a des risques connus et requiert une s&eacute;lection explicite des couches de test.'),
    @('Minimum, optional, deferred, and rejected test layers are justified.', 'Les couches minimales, optionnelles, diff&eacute;r&eacute;es et rejet&eacute;es sont justifi&eacute;es.'),
    @('The change is purely editorial.', 'Le changement est purement &eacute;ditorial.'),
    @('Work moves from analysis to execution, or execution to closeout.', 'Le travail passe de l&rsquo;analyse &agrave; l&rsquo;ex&eacute;cution, ou de l&rsquo;ex&eacute;cution &agrave; la cl&ocirc;ture.'),
    @('The gate returns <code>go</code> or <code>no-go</code> with blockers and follow-up actions.', 'La passerelle retourne <code>go</code> ou <code>no-go</code> avec bloqueurs et actions de suivi.'),
    @('Implementation after <code>go</code>, or targeted repair after <code>no-go</code>.', 'Impl&eacute;mentation apr&egrave;s <code>go</code>, ou correction cibl&eacute;e apr&egrave;s <code>no-go</code>.'),
    @('The task is trivial and has no analysis, contract, test, or traceability impact.', 'La t&acirc;che est triviale et n&rsquo;a aucun impact d&rsquo;analyse, contrat, test ou tra&ccedil;abilit&eacute;.'),
    @('Feature status, behavior, specs, tests, or validation evidence changed.', 'Le statut de fonctionnalit&eacute;, le comportement, les specs, les tests ou les preuves de validation ont chang&eacute;.'),
    @('Global and component traceability documents reflect current evidence and gaps.', 'Les documents de tra&ccedil;abilit&eacute; globaux et composants refl&egrave;tent les preuves et lacunes courantes.'),
    @('No observable behavior, contract, status, or validation evidence changed.', 'Aucun comportement observable, contrat, statut ou preuve de validation n&rsquo;a chang&eacute;.'),
    @('A tracer bullet, feature slice, blitz integration, retrofit slice, or cycle is ending.', 'Un tracer bullet, une tranche de fonctionnalit&eacute;, une int&eacute;gration blitz, une tranche retrofit ou un cycle se termine.'),
    @('Status transitions, evidence, deferred work, residual risks, and next actions are explicit.', 'Transitions de statut, preuves, travail diff&eacute;r&eacute;, risques r&eacute;siduels et prochaines actions sont explicites.'),
    @('No next skill by default; reopen named follow-up work only.', 'Aucune comp&eacute;tence suivante par d&eacute;faut; rouvrir seulement le suivi nomm&eacute;.'),
    @('The work is still actively changing or required evidence is unavailable.', 'Le travail change encore activement ou les preuves requises ne sont pas disponibles.'),
    @('Historical plans, verbal history, or ad hoc implementation history contain reusable signal.', 'Des plans historiques, un historique verbal ou une histoire d&rsquo;impl&eacute;mentation ad hoc contiennent un signal r&eacute;utilisable.'),
    @('Reusable signal is extracted with confidence and source type.', 'Le signal r&eacute;utilisable est extrait avec confiance et type de source.'),
    @('The source is a current-session working plan.', 'La source est un plan de travail de session courante.'),
    @('Mined historical signal needs mapping into canonical destinations.', 'Le signal historique extrait doit &ecirc;tre mapp&eacute; vers les destinations canoniques.'),
    @('The <code>R-*</code> records mappings, canonical updates, residual gaps, and index status.', 'Le <code>R-*</code> consigne mappings, mises &agrave; jour canoniques, lacunes r&eacute;siduelles et statut d&rsquo;index.'),
    @('No reusable signal exists, or the source already lives in current canon.', 'Aucun signal r&eacute;utilisable n&rsquo;existe, ou la source vit d&eacute;j&agrave; dans le canon courant.'),
    @('HTML companions to Markdown source documents, created when visual richness materially improves clarity and conceptual alignment between human and AI collaborators. The base methodology remains Markdown-first &mdash; rich views are deliberate supplements, not replacements.', 'Compagnons HTML aux documents Markdown sources, cr&eacute;&eacute;s lorsque la richesse visuelle am&eacute;liore mat&eacute;riellement la clart&eacute; et l&rsquo;alignement conceptuel entre collaborateurs humains et IA. La base m&eacute;thodologique reste Markdown-first &mdash; les vues riches sont des suppl&eacute;ments d&eacute;lib&eacute;r&eacute;s, pas des remplacements.'),
    @('When to create a rich view', 'Quand cr&eacute;er une vue riche'),
    @('Complex entity relationships', 'Relations d&rsquo;entit&eacute;s complexes'),
    @('multiple interdependent entity types with lifecycle interactions that are hard to follow in prose or tables alone.', 'plusieurs types d&rsquo;entit&eacute;s interd&eacute;pendants avec interactions de cycle de vie difficiles &agrave; suivre seulement en prose ou tableaux.'),
    @('State machines &amp; lifecycle governance', 'Machines d&rsquo;&eacute;tat et gouvernance de cycle de vie'),
    @('state transitions, activation/deactivation rules, parent-chain propagation across entity types.', 'transitions d&rsquo;&eacute;tat, r&egrave;gles d&rsquo;activation/d&eacute;sactivation, propagation en cha&icirc;ne parent entre types d&rsquo;entit&eacute;s.'),
    @('Cryptographic or protocol flows', 'Flux cryptographiques ou protocolaires'),
    @('multi-actor flows with key material transit, synchronization windows, and sequencing constraints where a diagram anchors understanding.', 'flux multi-acteurs avec transit de mat&eacute;riel de cl&eacute;, fen&ecirc;tres de synchronisation et contraintes de s&eacute;quen&ccedil;age o&ugrave; un diagramme ancre la compr&eacute;hension.'),
    @('High-impact architectural decisions', 'D&eacute;cisions architecturales &agrave; fort impact'),
    @('cross-cutting decisions with visual complexity that benefits from a durable graphical anchor.', 'd&eacute;cisions transversales avec complexit&eacute; visuelle qui b&eacute;n&eacute;ficient d&rsquo;un ancrage graphique durable.'),
    @('The methodology itself', 'La m&eacute;thodologie elle-m&ecirc;me'),
    @('the process and artifact system benefits from a visual reference accessible to both humans and AI agents at session start.', 'le processus et le syst&egrave;me d&rsquo;artefacts b&eacute;n&eacute;ficient d&rsquo;une r&eacute;f&eacute;rence visuelle accessible aux humains et aux agents IA au d&eacute;but d&rsquo;une session.'),
    @('Folder and linking convention', 'Convention de dossier et de lien'),
    @('Context', 'Contexte'),
    @('Path pattern', 'Pattern de chemin'),
    @('Link from Markdown parent', 'Lien depuis le parent Markdown'),
    @('Methodology (this file)', 'M&eacute;thodologie (ce fichier)'),
    @('Global docs (e.g. Lifecycle Governance)', 'Docs globaux (ex. Lifecycle Governance)'),
    @('Component-level', 'Niveau composant'),
    @('Design principles', 'Principes de conception'),
    @('Self-contained', 'Autonome'),
    @('no external CDN dependencies. Embed all styles and SVG inline.', 'aucune d&eacute;pendance CDN externe. Int&eacute;grer tous les styles et SVG inline.'),
    @('Sober palette', 'Palette sobre'),
    @('information first. Color is used for distinction, not decoration.', 'l&rsquo;information d&rsquo;abord. La couleur sert la distinction, pas la d&eacute;coration.'),
    @('Linked from the parent Markdown', 'Li&eacute; depuis le parent Markdown'),
    @('the source document carries a clear reference to the rich view.', 'le document source porte une r&eacute;f&eacute;rence claire vers la vue riche.'),
    @('Updated in the same changeset', 'Mis &agrave; jour dans le m&ecirc;me changeset'),
    @('when the parent document changes, the rich view is updated alongside it.', 'lorsque le document parent change, la vue riche est mise &agrave; jour avec lui.'),
    @('Single <code>index.html</code>', 'Un seul <code>index.html</code>'),
    @('per view folder; additional files only when navigation genuinely adds value for the specific topic.', 'par dossier de vue; fichiers additionnels seulement si la navigation ajoute vraiment de la valeur pour le sujet.'),
    @('Ezkey Methodology Rich View', 'M&eacute;thodologie Ezkey Vue riche'),
    @('Last updated 2026-05-25', 'Derni&egrave;re mise &agrave; jour le 2026-05-25')
)

$frNavBlock    = Apply-Replacements $navBlock $frPairs
$frHeaderBlock = Apply-Replacements $headerBlock $frPairs
$frMainBlock   = Apply-Replacements $mainBlock $frPairs
$frFooterBlock = Apply-Replacements $footerBlock $frPairs

$frMainBlock = $frMainBlock.Replace(
  ('open ' + $arrow + ' resumed ' + $arrow + ' complete, with post-decision notes when superseded'),
  ('ouverte ' + $arrow + ' reprise ' + $arrow + ' compl&egrave;te, avec notes post-d&eacute;cision quand elle est remplac&eacute;e'))
$frMainBlock = $frMainBlock.Replace(
  ('Any ' + $emDash + ' when a non-obvious methodology rule is adopted'),
  ('N&rsquo;importe quand ' + $emDash + ' quand une r&egrave;gle m&eacute;thodologique non &eacute;vidente est adopt&eacute;e'))
$frMainBlock = $frMainBlock.Replace(
  ('Stable ' + $emDash + ' never revised in place (supersede with new file)'),
  ('Stable ' + $emDash + ' jamais r&eacute;vis&eacute;e en place (remplacer avec un nouveau fichier)'))
$frMainBlock = $frMainBlock.Replace(
  ('Any ' + $emDash + ' when visual richness materially adds clarity'),
  ('N&rsquo;importe quand ' + $emDash + ' quand la richesse visuelle ajoute mat&eacute;riellement de la clart&eacute;'))

$frHeaderBlock = $rx::Replace(
  $frHeaderBlock,
  '(?s)<p class="subtitle">.*?</p>',
  '  <p class="subtitle">' + "`n" +
  '    R&eacute;f&eacute;rence visuelle pour le flux de l&rsquo;id&eacute;ation &agrave; la livraison &mdash; de l&rsquo;id&eacute;e brute &agrave; la tranche cl&ocirc;tur&eacute;e,' + "`n" +
  '    avec les valeurs m&eacute;thodologiques, les types d&rsquo;artefacts, les fronti&egrave;res d&rsquo;&eacute;tat, la tra&ccedil;abilit&eacute; et les comp&eacute;tences.' + "`n" +
  '  </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="values">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="values">' + "`n" +
  '    <h2>Valeurs m&eacute;thodologiques</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Les valeurs canoniques vivent dans le corpus m&eacute;thodologique Ezkey.' + "`n" +
  '      Cette vue garde les tests op&eacute;ratoires visibles&nbsp;: assez de rigueur pour pr&eacute;server le jugement et la tra&ccedil;abilit&eacute;,' + "`n" +
  '      sans assez de c&eacute;r&eacute;monie pour ralentir le travail simple.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="workflow">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="workflow">' + "`n" +
  '    <h2>Flux de travail de bout en bout <span class="lane-a-tag">Couloir A</span></h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Neuf phases explicites avec sorties d&rsquo;artefacts et crit&egrave;res de sortie. Passez &agrave; l&rsquo;impl&eacute;mentation d&egrave;s que' + "`n" +
  '      la direction, la premi&egrave;re coupe, les crit&egrave;res de validation et les questions ouvertes non bloquantes sont &eacute;tablis' + "`n" +
  '      &mdash; en &eacute;vitant autant le code pr&eacute;matur&eacute; que la pr&eacute;paration perp&eacute;tuelle.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="artifacts">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="artifacts">' + "`n" +
  '    <h2>Types d&rsquo;artefacts</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Tous les artefacts canoniques utilisent des identifiants date+slug. Aucun compteur global n&rsquo;est requis.' + "`n" +
  '      Les identifiants sont stables ' + $emDash + ' jamais renomm&eacute;s apr&egrave;s cr&eacute;ation, jamais r&eacute;utilis&eacute;s.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="lanes">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="lanes">' + "`n" +
  '    <h2>Couloirs parall&egrave;les</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Deux couloirs s&rsquo;ex&eacute;cutent en parall&egrave;le du flux principal d&rsquo;id&eacute;ation &agrave; livraison.' + "`n" +
  '      Une session peut combiner le couloir A avec l&rsquo;un ou les deux couloirs parall&egrave;les.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="collab">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="collab">' + "`n" +
  '    <h2>Contexte de collaboration</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Pas un couloir &mdash; un modificateur transversal. Ces r&egrave;gles s&rsquo;appliquent en plus des couloirs A, B et C' + "`n" +
  '      quand la collaboration se fait sur des branches Git parall&egrave;les ou des worktrees.' + "`n" +
  '      Les sessions &agrave; branche unique n&rsquo;ont pas besoin de les appliquer.' + "`n" +
  '    </p>',
  $rxOpts)
$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<div class="collab-modifier-tag">.*?</div>',
  '<div class="collab-modifier-tag">Transversal &middot; s&rsquo;applique aux couloirs A, B, C</div>',
  $rxOpts)
$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<p class="collab-desc">.*?</p>',
  '<p class="collab-desc">' + "`n" +
  '        Quand deux d&eacute;veloppeurs (ou un d&eacute;veloppeur sur deux worktrees) travaillent simultan&eacute;ment,' + "`n" +
  '        le nommage ordinal classique cr&eacute;e des collisions &agrave; la fusion. Le contexte de collaboration' + "`n" +
  '        r&eacute;sout cela sans surcharge de coordination, en rendant les noms d&rsquo;artefacts autosuffisants' + "`n" +
  '        par les identifiants date+slug et en diff&eacute;rant les index partag&eacute;s apr&egrave;s la fusion.' + "`n" +
  '      </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="naming">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="naming">' + "`n" +
  '    <h2>Conventions de nommage</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Identifiants stables et r&eacute;sistants aux collisions sur toutes les branches et worktrees.' + "`n" +
  '      Les artefacts h&eacute;rit&eacute;s <code>NNNN</code> ne sont jamais renomm&eacute;s.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="skills">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="skills">' + "`n" +
  '    <h2>Contrats de fronti&egrave;res des comp&eacute;tences</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Chaque comp&eacute;tence r&eacute;pond aux quatre m&ecirc;mes questions&nbsp;: entrer quand, sortir quand, appeler ensuite' + "`n" +
  '      et inutile quand. Le texte canonique des comp&eacute;tences vit sous <code>.cursor/skills/</code>; ce tableau est' + "`n" +
  '      la surface de navigation rapide.' + "`n" +
  '    </p>',
  $rxOpts)

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="rich-views">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="rich-views">' + "`n" +
  '    <h2>Vues riches</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Compagnons HTML aux documents Markdown sources, cr&eacute;&eacute;s lorsque la richesse visuelle am&eacute;liore mat&eacute;riellement' + "`n" +
  '      la clart&eacute; et l&rsquo;alignement conceptuel entre collaborateurs humains et IA. La base m&eacute;thodologique reste' + "`n" +
  '      Markdown-first &mdash; les vues riches sont des suppl&eacute;ments d&eacute;lib&eacute;r&eacute;s, pas des remplacements.' + "`n" +
  '    </p>',
  $rxOpts)

$frCopyPairs = @(
  @(('draft ' + $arrow + ' under-review ' + $arrow + ' promoted / archived'), ('brouillon ' + $arrow + ' en revue ' + $arrow + ' promu / archiv&eacute;')),
  @(('captured ' + $arrow + ' triaged ' + $arrow + ' incubating ' + $arrow + ' ready ' + $arrow + ' active ' + $arrow + ' done / parked / archived / dropped'), ('captur&eacute;e ' + $arrow + ' tri&eacute;e ' + $arrow + ' en incubation ' + $arrow + ' pr&ecirc;te ' + $arrow + ' active ' + $arrow + ' termin&eacute;e / mise en attente / archiv&eacute;e / abandonn&eacute;e')),
  @(('captured ' + $arrow + ' mapped ' + $arrow + ' integrated ' + $arrow + ' archived'), ('captur&eacute;e ' + $arrow + ' cartographi&eacute;e ' + $arrow + ' int&eacute;gr&eacute;e ' + $arrow + ' archiv&eacute;e')),
  @('Non-canonical ' + $emDash + ' materialized into V-*/I-*/TB-*', 'Non canonique ' + $emDash + ' mat&eacute;rialis&eacute; en V-*/I-*/TB-*'),
  @('Active (<code>_</code> prefix) ' + $arrow + ' archived (no prefix, in <code>blitz-archive/</code>)', 'Actif (pr&eacute;fixe <code>_</code>) ' + $arrow + ' archiv&eacute; (sans pr&eacute;fixe, dans <code>blitz-archive/</code>)'),
  @('Companion to its Markdown parent ' + $emDash + ' updated with it', 'Compagnon du document Markdown parent ' + $emDash + ' mis &agrave; jour avec lui'),
  @('free name', 'nom libre'),
  @('<code>plans/</code> or <code>.cursor/plans/</code>', '<code>plans/</code> ou <code>.cursor/plans/</code>'),
  @('Blitz intake', 'Capture blitz'),
  @('V-*, I-*, TB-*, R-* ' + $emDash + ' no cross-branch coordination needed.', 'V-*, I-*, TB-*, R-* ' + $emDash + ' aucune coordination entre branches n&rsquo;est n&eacute;cessaire.'),
  @('On a feature branch, topic slug is chosen at session start ' + $emDash + ' not the ordinal fallback.', 'Sur une branche de fonctionnalit&eacute;, le slug de sujet est choisi au d&eacute;but de la session ' + $emDash + ' pas par le repli ordinal.'),
  @('Blitz ' + $emDash + ' feature branch', 'Blitz ' + $emDash + ' branche de fonctionnalit&eacute;'),
  @('Blitz ' + $emDash + ' main / single-branch', 'Blitz ' + $emDash + ' main / branche unique'),
  @('<td>Same</td>', '<td>Identique</td>'),
  @('<em>or</em>', '<em>ou</em>'),
  @('Must include "Resume at" control block', 'Doit inclure un bloc de reprise &laquo;&nbsp;Resume at&nbsp;&raquo;'),
  @('The workflow is not only a forward pipeline. Cl&ocirc;ture and audit must also prove where an artifact came from, what canon absorbed it, and whether later decisions changed its meaning.', 'Le flux n&rsquo;est pas seulement un pipeline vers l&rsquo;avant. La cl&ocirc;ture et l&rsquo;audit doivent aussi prouver d&rsquo;o&ugrave; vient un artefact, quel canon l&rsquo;a absorb&eacute; et si des d&eacute;cisions ult&eacute;rieures ont chang&eacute; son sens.'),
  @('Session grill, open questions, decision pressure points, status alignment.', 'Session grill, questions ouvertes, points de pression d&eacute;cisionnels, alignement des statuts.'),
  @('<code>ezkey-backlog-triage</code> or <code>ezkey-grill-me</code>', '<code>ezkey-backlog-triage</code> ou <code>ezkey-grill-me</code>'),
  @('<code>ezkey-grill-me</code> or <code>ezkey-tracer-bullet-promote</code>', '<code>ezkey-grill-me</code> ou <code>ezkey-tracer-bullet-promote</code>'),
  @('<code>ezkey-component-design-pack</code> and <code>ezkey-test-strategy-planner</code>', '<code>ezkey-component-design-pack</code> et <code>ezkey-test-strategy-planner</code>'),
  @('<code>ezkey-test-strategy-planner</code>, then <code>ezkey-quality-gatekeeper</code>', '<code>ezkey-test-strategy-planner</code>, puis <code>ezkey-quality-gatekeeper</code>'),
  @('<code>ezkey-quality-gatekeeper</code>, then traceability after evidence changes.', '<code>ezkey-quality-gatekeeper</code>, puis synchronisation de la tra&ccedil;abilit&eacute; si les preuves changent.'),
  @('Impl&eacute;menteration after <code>go</code>, or targeted repair after <code>no-go</code>.', 'Impl&eacute;mentation apr&egrave;s <code>go</code>, ou correction cibl&eacute;e apr&egrave;s <code>no-go</code>.'),
  @('Implementation after <code>go</code>, or targeted repair after <code>no-go</code>.', 'Impl&eacute;mentation apr&egrave;s <code>go</code>, ou correction cibl&eacute;e apr&egrave;s <code>no-go</code>.'),
  @('<code>ezkey-traceability-sync</code> or <code>ezkey-closeout</code>', '<code>ezkey-traceability-sync</code> ou <code>ezkey-closeout</code>'),
  @('briefs + mappings', 'notes + cartographies'),
  @('Mapper dans les docs canoniques', 'Cartographier dans les docs canoniques'),
  @('patterns et signaux de test', 'sch&eacute;mas r&eacute;currents et signaux de test'),
  @('Triage, promotion tracer bullet ou design composant.', 'Triage, promotion de tranche t&eacute;moin ou conception de composant.'),
  @('Intake de vision, triage backlog ou promotion tracer bullet.', 'Prise de vision, triage du backlog ou promotion de tranche t&eacute;moin.'),
  @('Un tracer bullet, une tranche de fonctionnalit&eacute;, une int&eacute;gration blitz, une tranche retrofit ou un cycle se termine.', 'Une tranche t&eacute;moin, une tranche de fonctionnalit&eacute;, une int&eacute;gration blitz, une tranche de r&eacute;int&eacute;gration ou un cycle se termine.'),
  @('Le <code>R-*</code> consigne mappings, mises &agrave; jour canoniques, lacunes r&eacute;siduelles et statut d&rsquo;index.', 'Le <code>R-*</code> consigne les cartographies, les mises &agrave; jour canoniques, les lacunes r&eacute;siduelles et le statut d&rsquo;index.'),
  @('Retrofit historique', 'R&eacute;int&eacute;gration historique'),
  @('Tranche de retrofit', 'Tranche de r&eacute;int&eacute;gration'),
  @('Couloir retrofit', 'Couloir de r&eacute;int&eacute;gration'),
  @('entr&eacute;e de retrofit historique', 'entr&eacute;e de r&eacute;int&eacute;gration historique'),
  @('Vue riche</h2>', 'Vue enrichie</h2>'),
  @('Vues riches</h2>', 'Vues enrichies</h2>'),
  @('M&eacute;thodologie Ezkey Vue riche', 'Vue enrichie de la m&eacute;thodologie Ezkey')
)

$frMainBlock = Apply-Replacements $frMainBlock $frCopyPairs
$frFooterBlock = Apply-Replacements $frFooterBlock $frCopyPairs

$frMainBlock = $frMainBlock.Replace('Tracer bullet', 'Tranche t&eacute;moin')
$frMainBlock = $frMainBlock.Replace('tracer bullet', 'tranche t&eacute;moin')
$frMainBlock = $frMainBlock.Replace(
  ('Companion to its Markdown parent ' + $emDash + ' updated with it'),
  ('Compagnon du document Markdown parent ' + $emDash + ' mis &agrave; jour avec lui'))
$frMainBlock = $frMainBlock.Replace('Ne pas traiter comme du retrofit sauf si la source est vraiment historique', 'Ne pas traiter comme une r&eacute;int&eacute;gration historique sauf si la source est vraiment historique')
$frMainBlock = $frMainBlock.Replace('Enregistrer une tranche de retrofit (R-*)', 'Enregistrer une tranche de r&eacute;int&eacute;gration (R-*)')
$frMainBlock = $frMainBlock.Replace(
  ('V-*, I-*, TB-*, R-* ' + $emDash + ' no cross-branch coordination needed.'),
  ('V-*, I-*, TB-*, R-* ' + $emDash + ' aucune coordination entre branches n&rsquo;est n&eacute;cessaire.'))
$frMainBlock = $frMainBlock.Replace(
  ('On a feature branch, topic slug is chosen at session start ' + $emDash + ' not the ordinal fallback.'),
  ('Sur une branche de fonctionnalit&eacute;, le slug de sujet est choisi au d&eacute;but de la session ' + $emDash + ' pas par le repli ordinal.'))
$frMainBlock = $frMainBlock.Replace(('Blitz ' + $emDash + ' feature branch'), ('Blitz ' + $emDash + ' branche de fonctionnalit&eacute;'))
$frMainBlock = $frMainBlock.Replace(('Blitz ' + $emDash + ' main / single-branch'), ('Blitz ' + $emDash + ' main / branche unique'))
$frMainBlock = $frMainBlock.Replace('grill, retrofit ou impl&eacute;mentation', 'grill, r&eacute;int&eacute;gration ou impl&eacute;mentation')
$frMainBlock = $frMainBlock.Replace('fronti&egrave;res composant, mappings, validations', 'fronti&egrave;res composant, cartographies, validations')
$frMainBlock = $frMainBlock.Replace('Markdown-first', 'Markdown d&rsquo;abord')
$frMainBlock = $frMainBlock.Replace('Vue riche</h2>', 'Vue enrichie</h2>')
$frMainBlock = $frMainBlock.Replace('Vues riches</h2>', 'Vues enrichies</h2>')
$frNavBlock = $frNavBlock.Replace('Vues riches', 'Vues enrichies')
$frFooterBlock = $frFooterBlock.Replace('M&eacute;thodologie Ezkey Vue riche', 'Vue enrichie de la m&eacute;thodologie Ezkey')

$frMainBlock = $frMainBlock.Replace(('Non-canonical ' + $emDash + ' materialized into V-*/I-*/TB-*'), ('Non canonique ' + $emDash + ' mat&eacute;rialis&eacute; en V-*/I-*/TB-*'))
$frMainBlock = $frMainBlock.Replace(('Active (<code>_</code> prefix) ' + $arrow + ' archived (no prefix, in <code>blitz-archive/</code>)'), ('Actif (pr&eacute;fixe <code>_</code>) ' + $arrow + ' archiv&eacute; (sans pr&eacute;fixe, dans <code>blitz-archive/</code>)'))
$frMainBlock = $frMainBlock.Replace('premi&egrave;re coupe', 'premier d&eacute;coupage')
$frMainBlock = $frMainBlock.Replace('IDs date+slug obligatoires', 'Identifiants date+slug obligatoires')
$frMainBlock = $frMainBlock.Replace('pas par le repli ordinal', 'pas par la num&eacute;rotation ordinale de secours')
$frMainBlock = $frMainBlock.Replace('Le signal historique extrait doit &ecirc;tre mapp&eacute; vers les destinations canoniques.', 'Le signal historique extrait doit &ecirc;tre cartographi&eacute; vers les destinations canoniques.')
$frMainBlock = $frMainBlock.Replace('impacts docs nomm&eacute;s.', 'impacts documentaires nomm&eacute;s.')
$frMainBlock = $frMainBlock.Replace('les specs,', 'les sp&eacute;cifications,')
$frMainBlock = $frMainBlock.Replace('action suivante ou supersession.', 'action suivante ou remplacement.')
$frMainBlock = $frMainBlock.Replace('la source vit d&eacute;j&agrave; dans le canon courant.', 'la source fait d&eacute;j&agrave; partie du canon courant.')
$frMainBlock = $frMainBlock.Replace('Pattern de chemin', 'Mod&egrave;le de chemin')
$frMainBlock = $frMainBlock.Replace('Docs globaux', 'Documents globaux')
$frMainBlock = $frMainBlock.Replace('SVG inline', 'SVG int&eacute;gr&eacute;s')
$frMainBlock = $frMainBlock.Replace('Int&eacute;grer tous les styles et SVG int&eacute;gr&eacute;s.', 'Int&eacute;grer tous les styles et tous les SVG.')
$frMainBlock = $frMainBlock.Replace('m&ecirc;me changeset', 'm&ecirc;me ensemble de changements')
$frMainBlock = $frMainBlock.Replace('Vue riche](view/', 'Vue enrichie](view/')
$frMainBlock = $frMainBlock.Replace('Vue riche](view', 'Vue enrichie](view')
$frMainBlock = $frMainBlock.Replace('Vue riche', 'Vue enrichie')
$frMainBlock = $frMainBlock.Replace('vue riche', 'vue enrichie')
$frMainBlock = $frMainBlock.Replace('vues riches', 'vues enrichies')
$frFooterBlock = $frFooterBlock.Replace('Vue riche', 'Vue enrichie')
$frFooterBlock = $frFooterBlock.Replace('vue riche', 'vue enrichie')

$frMainBlock = $rx::Replace(
  $frMainBlock,
  '(?s)<section id="traceability">\s*<h2>.*?</h2>\s*<p class="section-intro">.*?</p>',
  '<section id="traceability">' + "`n" +
  '    <h2>Tra&ccedil;abilit&eacute; bidirectionnelle</h2>' + "`n" +
  '    <p class="section-intro">' + "`n" +
  '      Le flux n&rsquo;est pas seulement un pipeline vers l&rsquo;avant. La cl&ocirc;ture et l&rsquo;audit doivent aussi prouver' + "`n" +
  '      d&rsquo;o&ugrave; vient un artefact, quel canon l&rsquo;a absorb&eacute; et si des d&eacute;cisions ult&eacute;rieures ont chang&eacute; son sens.' + "`n" +
  '    </p>',
  $rxOpts)

$outputFr = @"
<!DOCTYPE html>
<!-- Source: product-docs/methodology/view/index.html @ $syncDate -->
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="description" content="$descriptionFr">
  <meta property="og:title" content="M&eacute;thodologie Ezkey &mdash; Vue enrichie">
  <meta property="og:description" content="$descriptionFr">
  <meta property="og:url" content="https://ezkey.org/fr/methodologie.html">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="Ezkey">
  <meta property="og:locale" content="fr_FR">
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="M&eacute;thodologie Ezkey &mdash; Vue enrichie">
  <meta name="twitter:description" content="$descriptionFr">
  <meta name="twitter:image" content="https://ezkey.org/logo.svg">
  <link rel="alternate" hreflang="en" href="https://ezkey.org/methodology.html">
  <link rel="alternate" hreflang="fr" href="https://ezkey.org/fr/methodologie.html">
  <link rel="alternate" hreflang="x-default" href="https://ezkey.org/methodology.html">
  <title>M&eacute;thodologie Ezkey &mdash; Vue enrichie</title>
  <style>
$siteShellCss
    /* Canonical styles (from product-docs/methodology/view/index.html) */
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
$explorerCtaFr
$frMainBlock
$explorerReminderFr
$frFooterBlock
    </div>

    <footer class="site-footer" style="text-align:center;">
      Ezkey &nbsp;&middot;&nbsp; <a href="/fr/">ezkey.org</a>
    </footer>
  </div>
</body>
</html>
"@

Write-Page $outputPathEn $outputEn 'EN'
Write-Page $outputPathFr $outputFr 'FR'
Write-Host "Sync:         Source: product-docs/methodology/view/index.html @ $syncDate"
Write-Host "Next steps:   open sites/ezkey-org/methodology.html and fr/methodologie.html, then deploy preview."

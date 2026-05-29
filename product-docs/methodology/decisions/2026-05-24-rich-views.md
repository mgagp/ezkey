---
public: true
---
# Rich Views — HTML Companions for Complex Documentation

## Date

2026-05-24

## Context

During a meta-methodology session, the operator observed a convergence of two trends: software
engineers at Anthropic and in the broader AI-tooling ecosystem increasingly valuing HTML over
Markdown for communication precision and expressivity; and the emerging practice of human–AI
collaborations producing self-contained HTML micro-applications as communication anchors.

The question raised was whether the Ezkey methodology could pragmatically integrate this capability
as a complement to the existing Markdown-first documentation corpus — not to generate heavy
documentation overhead, but as a deliberate communication tool for topics where visual richness
materially improves conceptual convergence between human and AI collaborators.

The `LIFECYCLE_GOVERNANCE.md` document was cited as the canonical example: a cross-entity analysis
expressed through structured tables that enabled minimalist, reliable, testable implementation.
The operator's thesis: when clarity is well-represented visually, code becomes simple, resilient,
easy to implement, easy to test, and intellectually integral.

A second candidate topic raised was the Google Tink cryptographic key rotation protocol — including
the key blob transit between the MasterKey (Docker-mounted file / AWS Secret), the database
(active + drained keys), and the multi-instance synchronization window — as a flow that demands
visual anchoring to be truly mastered.

The operator also explicitly requested that methodology decisions preserve verbatim source
statements for historical reference, in the style of blitz-archive entries.

## Historical interpretation note (optional)

This record preserves an earlier state of the methodology rich view discussion. References below to
the number, lettering, or role of lanes reflect the workflow structure that existed on 2026-05-24.
Current canonical lane semantics and lettering now live in the current methodology docs and later
lane-ordering decisions.

## Decision

### Canonical term

The artifact type is named **rich view** (not "micro-site"). The term "micro-site" was discarded
to avoid ambiguity with the actual product sites in the `sites/` directory (Cloudflare Pages,
public website). "Rich view" is precise: an HTML companion that provides a richer visual
representation of a specific document or concept cluster.

### Folder structure

Rich views are colocated with their parent content using a standard `view/` subfolder:

```
product-docs/methodology/view/index.html      ← methodology rich view
docs/view/<slug>/index.html                   ← global-level rich views (e.g. lifecycle-governance)
product-docs/components/<comp>/view/index.html ← component-level rich views
```

When a section only has one rich view, `view/index.html` is the entry point directly.
When a section has multiple rich views (uncommon), a slug subfolder is introduced:
`view/<slug>/index.html`.

### Linking convention

The parent Markdown document references its rich view with a clear inline link. No global
registry is required — path-addressability is sufficient.

### When to create a rich view

A rich view is warranted when **at least one** of the following is true:

- Multiple interdependent entity types have complex lifecycle interactions that are difficult to
  follow in prose or tables alone.
- A state machine, activation/deactivation rule set, or parent-chain propagation is at play.
- A cryptographic or protocol flow involves multiple actors, key material transit, or sequencing
  constraints.
- A high-impact architectural decision has cross-cutting visual complexity.
- The methodology itself (the process and artifact system) benefits from a visual reference
  accessible to both humans and AI agents.

### Design principles for rich views

- Self-contained HTML — no external CDN dependencies. Embed styles and SVG inline.
- Sober palette — information first. Color is used for distinction, not decoration.
- Linked from the parent Markdown document with a clear reference line.
- Updated in the same changeset as the parent document when content changes.
- Single `index.html` per view folder; additional files only when navigation genuinely adds value.

### First rich view

The first rich view is the methodology itself:
`product-docs/methodology/view/index.html` — visual reference for the end-to-end workflow,
artifact types, parallel lanes, naming conventions, and skills.

## Options considered

| Option | Advantages | Disadvantages |
|---|---|---|
| "micro-site" (original term) | Intuitive colloquially | Collision with `sites/` product sites in this repo |
| "rich view" (chosen) | Precise; no ambiguity; fits the scope | Slightly less common |
| "visual companion" | Descriptive | Longer; not a natural slug |
| "HTML panel" | Technical precision | Too UI-widget-like |

| Placement option | Advantages | Disadvantages |
|---|---|---|
| Colocated `view/` subfolder | Natural maintenance; clear ownership; no central registry | Requires navigating to parent first |
| Central `product-docs/rich-views/` directory | Easy to list all views | Breaks colocation; orphan risk when parent moves |
| Colocated flat file (e.g. `lifecycle-governance.html`) | Simplest | Mixes HTML and Markdown in same directory; no room for multi-file views |

## Verbatim source (operator statements — original language preserved)

### Session 1 — 2026-05-24 — Initial proposal

> J'ai observé depuis quelques temps que les ingénieurs logiciels chez Anthropic ont commencé à
> mettre en valeur l'HTML au profit de la clarté et de son expressivité, et auparavant tout était
> capitalisé sur le Markdown, qui demeure quand même un format fortement accepté et très pragmatique.
> Mais évidemment, ce n'est pas la même richesse que l'HTML.

> je crois qu'on a une opportunité simple, pratique, pragmatique, efficace de bonifier notre
> approche méthodologique en mettant à profit cette notion pour l'intégrer proprement. Pas pour
> générer une tonne de documentation et des dizaines de petits graphiques jolis, non, mais comme
> étant un point d'appui pour l'expressivité et la clarté, pour favoriser la convergence
> conceptuelle, l'unification des idées, la convergence des idées entre humain et éail.

> On doit créer la clarté et, lorsque la clarté est bien représentée, alors le code devient simple,
> résilient, facile à implémenter, facile à tester et intellectuellement intègre. Ce sont ces
> qualités que l'on doit rechercher en tout temps dans le projet.

> j'aimerais que l'on s'assure que, lorsqu'on prend les notes pour les décisions méthodologiques,
> il y a une partie, à l'image des blitz, où tu vas conserver verbatim mes propos de façon
> appropriée, pour qu'il soit en référence historique.

> Ce que je vois, c'est, à l'image de ce que l'on fait déjà, qu'on pourrait créer un sous-dossier
> avec un hommage approprié et faire le lien avec le document [...] À l'intérieur du microsite,
> qui prend la forme de ce dossier avec un hommage précis, on retrouve à l'intérieur le index.html,
> ainsi que le ou les fichiers avec la navigation simple appropriée.

> Du point de vue du choix de palette de couleur et de CSS, je crois qu'il faut garder cela sobre.
> Il faut que l'information ressorte clairement pour ce qui concerne ce que l'on veut faire
> ressortir.

> il faut qu'on se pose la question [sur le terme]. Un autre exemple qui me vient à l'esprit,
> c'est l'implémentation de Google Tink pour la cryptographie interne. C'est un sujet aride mais
> combien important, essentiel, qui doit être parfaitement maîtrisé, et il y a quand même quelques
> pièces mouvantes.

### Session 1 — follow-up — Priority confirmation

> Ah oui, il y a assurément, en termes de méta-méthodologies, une opportunité d'avoir un
> micro-site qui va documenter notre méthodologie. Alors, je crois que tu conviendras que c'est
> de la plus haute pertinence, et ça devrait aussi être notre tout premier objectif.

### Session 1 — follow-up — Term and structure approval

> Tes propositions sont d'une très haute pertinence. C'est parfait. Je suis entièrement d'accord
> avec la terminologie "rich view". Honnêtement, je ne tenais pas du tout à "micro-site". C'est
> simplement un terme que j'avais déjà entendu. Ce qui est important, c'est la clarté, et c'est
> ce que tu as à amener. C'est parfait. Le sous-dossier "standard view" avec le mode de
> référencement, c'est parfait. Donc, on avance dans cet ordre.

### Session 1 — follow-up — Lane A label (rich view refinement)

*Context: after reviewing the published rich view, the operator noticed that Lane B/C/D appeared
without a visible Lane A, and asked why the numbering did not start at A.*

> Simple curiosité : je consulte l'index.html pour la rich view méthodologie et je vois que tu as
> nommé les parallèles lanes comme étant lane B, C et D. Juste comme ça, peux-tu m'expliquer
> pourquoi c'est B, C et D, pourquoi pas A, B, C ? Je ne te demande pas de changer ça, mais,
> dans un premier temps, simplement me faire comprendre, parce que si je présente notre approche
> méthodologique à quelqu'un et que je m'appuie sur cette documentation visuelle, quelqu'un
> pourrait me demander : « Mais pourquoi ce n'est pas A, B, C ? Pourquoi ça démarre à B ? »

*Resolution: Lane A label added as an inline badge on the Workflow section heading to make the
implicit Lane A explicit.*

> Parfait, c'est très clair. Je suis d'accord que l'option 1 semble la plus correcte. Je crois
> que ce qui est important, c'est de donner vie à ce lane A implicite, et ce que tu proposes
> accomplit cela.

### Session 1 — follow-up — Collaboration Context restructuring

*Context: the operator questioned the conceptual integrity of treating multi-branch/worktree as
"Lane D" at the same level as Lane B (Plan Incubation) and Lane C (Legacy Retrofit).*

> Une autre question que j'ai pour toi : on indique que le Lane A, manifestement, ça concerne la
> méthodologie pour elle-même. Parfait. Le Lane B et C, c'est très clair. La seule chose qui
> m'intrigue un petit peu, comme positionnement en termes de terminologies, c'est le Lane D :
> c'est un aspect que l'on a ajouté pour clarté méthodologique, le mode multibranche avec
> WorkTree. Je vois ça comme étant un aspect qui va de pair avec le Lane A, B ou C, en fait
> surtout B ou C, parce que je crois que je vais m'imposer comme contrainte qu'il n'y a pas de
> parallélisme multi-branches WorkTree lorsque on fait du travail méthodologique. J'aimerais
> t'entendre sur le choix de représenter les éléments relevants de la méthode de travail
> multibranch, multi-développeur en tant que nomenclature au niveau Lane.

*On the choice of term "Collaboration Context" over "Multi-Branch":*

> Je crois que Collaboration/Context est le bon terme. Multi-Branch, ce n'est pas faux, mais ça
> entre dans le commun, alors que Collaboration/Context rend explicite le fait de vouloir
> s'exprimer sur la collaboration, peu importe la forme que prend cette collaboration. Donc,
> c'est meilleur. Et alors donc, oui, on applique cette restructuration maintenant qu'on a un
> alignement conceptuel d'idées bien alignées.

*Resolution: Lane D removed from the Parallel Lanes group. A new standalone "Collaboration
Context" section was created with distinct visual treatment (slate color, cross-cutting modifier
badge, rule grid) and its own nav entry. The Parallel Lanes section now correctly states "Two
lanes" and references Lane A explicitly.*

### Session 1 — follow-up — Verbatim preservation mandate extended

> Parfait. Dans la prise de notes verbatim de l'évolution méthodologique, j'aimerais que tu
> inclues mes propos sur ce que l'on vient d'accomplir pour garder aussi trace de cette
> collaboration entre toi et moi.

## Cross-references

- Rich view produced by this decision: [`product-docs/methodology/view/index.html`](../view/index.html)
- Candidate future rich view: `docs/view/lifecycle-governance/` (lifecycle governance cross-entity model)
- Candidate future rich view: `docs/view/tink-key-rotation/` (Google Tink key rotation protocol and synchronization window)

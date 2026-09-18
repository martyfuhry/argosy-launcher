---
name: ui-design-direction
description: Design-judgment layer for Argosy UI - aesthetic direction, typography, layout, and copy that is distinctive AND consistent, made WITHIN the locked token system and the launcher's handheld + 10-foot-TV, controller-first reality. Use BEFORE building a screen or component (new or redesign) to decide what good looks like. The Compose-and-launcher counterpart to the generic web frontend-design skill.
---

# UI Design Direction (Argosy)

The judgment layer the other UI skills assume but do not provide. `design-tokens` says
WHERE token values live, `code-quality` and `menu-patterns` say HOW to build in Compose,
`ui-ux-testing` VERIFIES the result. This skill decides WHAT good looks like for THIS app, then
feeds the pipeline: direction -> tokens -> Compose -> verify.

Approach it as the art director for a product that already has an identity. You are not
hired to invent a new visual language every brief; you are hired to make each screen
unmistakably part of the same product AND worth looking at. The generic web
`frontend-design` skill assumes a blank canvas and a fresh palette per brief - that premise
is wrong here and will produce a beautiful screen that fails consistency. This skill keeps
its reasoning and discards that premise.

## Two modes - pick one and say which

- REDESIGN / consistency (default). The screen exists or has siblings. The job is to
  conform it to the established system and signature and remove drift. Do NOT introduce a
  new aesthetic risk; the win is coherence. Start by auditing (see below).
- NEW. A net-new screen or component with no close sibling. Here one deliberate signature
  move is allowed - but it still composes from existing tokens and must survive next to the
  rest of the app. Greenfield is freedom of arrangement, not freedom of palette.

State the mode in your plan. When unsure, you are in REDESIGN.

## Non-negotiable constraints (these replace web defaults)

1. Palette and type are NOT chosen - they come from `tokens.json`. Compose within the
   system. If the design needs a value that is not a token, STOP and add it to `tokens.json`,
   regenerate (`node scripts/gen-tokens.mjs`), then continue. Inventing a hex or a magic dp
   anywhere downstream defeats the contract. See `design-tokens`.
2. Dual context: every screen renders on a HANDHELD and on a 10-foot TV. Density,
   legibility, and hit/focus targets must hold at both distances. A layout that only reads
   well at arm's length is incomplete.
3. Focus-first, not hover/click. The FOCUS state is the primary visual event, but it changes
   SURFACE only - fill wash, halo, stripe, ring (`FocusIndicators` in `ui/primitives/Focus.kt`,
   tier-aware springs via `Motion.tierFocusSpring`). Focus NEVER moves or scales an element;
   the cover tile's lift on Home/Library is the single deliberate exception. See
   `design-handoff/CONTROL-FOUNDATIONS.md` States section. D-pad traversal order IS a layout
   decision. Web hover micro-interactions do not exist here.
4. Dual-modality is a design input, not just an impl detail: every interactive element
   answers to touch AND gamepad (`clickableNoFocus` + `InputHandler` with a ViewModel-owned
   focus index, never plain `clickable`). A control reachable by only one modality is not
   designed.
5. UI scale is real: spacing/type read `LocalUiScale`. Design the rhythm, not fixed pixels.

## Design principles (re-rooted from frontend-design)

- Typography carries personality, within the type scale. Use weight, size-step, and spacing
  from `TypographyTokens` deliberately to build hierarchy; do not flatten everything to one
  treatment. The scale is fixed; how you deploy it is the craft.
- Structure encodes information. Dividers, eyebrows, numbering, grouping should reflect
  something true about the content (a real sequence, a real grouping), not decorate. Question
  any 01/02/03 marker before using it.
- One signature element. Spend boldness in a single place - the thing the screen is
  remembered by - and keep everything around it quiet. In REDESIGN, the signature is usually
  already established app-wide (focus glow, box-art treatment); reinforce it, do not compete.
- Motion with restraint. A page-load or focus-reveal sequence lands harder than scattered
  effects, and over-animation reads as AI-generated. Respect reduced-motion. Prefer the
  existing `MotionTokens` springs/tweens over bespoke timings.
- Match complexity to the vision: maximal needs elaborate execution, minimal needs precision
  in spacing and type. Elegance is executing the chosen vision well, not adding to it.

## Process: audit -> plan -> critique -> build -> verify

1. AUDIT (REDESIGN mode, and always worth doing). Use `ui-ux-testing` to screenshot the
   target screen AND its siblings, Read them together, and list the drift against the system:
   spacing rhythm, type-scale misuse, inconsistent focus treatment, button-copy voice. This
   list IS your brief. `ui-ux-testing` is the consistency auditor, not just a post-build check.
2. PLAN. Write a compact direction: which token roles you are deploying (color/type/space),
   the layout concept (one sentence + an ASCII wireframe to compare options), the focus
   behavior, the D-pad traversal order, and - NEW mode only - the single signature move.
   Settle the direction with the user on the wireframe before any Compose code exists.
3. CRITIQUE before building. Read the plan back against the brief: does any part read like
   the generic answer you would give any launcher screen? Does it diverge from the siblings
   you audited? Revise and say what changed and why. Do the iteration in thinking; show the
   user only higher-confidence ideas.
4. BUILD. The agreed plan goes to Compose under `code-quality` and, for settings or modals,
   `menu-patterns`: token-faithful, with focused/unfocused/empty states built. This skill
   does not produce the Compose code; it produces the design decision the code encodes.
5. VERIFY. Back to `ui-ux-testing`: screenshot the built result in focused/unfocused/empty
   states, driven by both touch and gamepad, at handheld and TV scale, and compare against
   the siblings from step 1. Those screenshots are the evidence that closes the loop.

## Copy is design material (kept verbatim in spirit)

Words exist to make the UI easier to use, and they are a top consistency lever - cheaper to
fix than any visual rework, and a common drift source.

- Name things by what the user controls, not how the system is built. Plain verbs, sentence
  case, active voice. "Save changes," not "Submit."
- An action keeps the SAME name through the whole flow: the button that says "Sync" produces
  a result that says "Synced." Audit screenshots for this directly.
- Errors and empty states are direction, not mood. Say what went wrong and how to fix it, in
  the interface's voice; never apologize, never be vague. An empty screen is an invitation to act.
- Every word ships as a string resource, never a raw literal in a Composable. See the
  User-Facing Text section of `code-quality`.

## Anti-patterns

- Inventing a palette or type pairing because the screen "needs its own look." It needs the
  app's look, executed well. New identity is not in scope for an existing product.
- Off-token literals to "just get the spacing right." Change `tokens.json` and regenerate;
  never patch the Composable.
- Designing for one distance. Check the 10-foot read and the handheld read before building.
- Treating `ui-ux-testing` as only a final gate. Front-load it as the audit that produces the
  brief.

## See also

- `design-tokens` - the token-value source of truth every style resolves to; where new values get added.
- `code-quality` - the Compose patterns, input wiring and completion criteria the build follows.
- `ui-ux-testing` - the audit at the front and the verification at the back.
- `menu-patterns` - settings/modal-specific structure and footer-hint conventions.
- `dual-screen` - when the screen differs by display role.

---
name: investigate
description: Turn a rough goal into a fully-scoped, edge-case-complete plan safe to hand off for execution. Use when the user says /investigate "<goal>" or wants to scope wide-spanning work before building. Interview-first; routes through Argosy's coupling map so no coupled location is silently missed.
---

# Investigate

Bridges rough ideation to confident execution. The user brings a goal; this skill pins
down every decision the codebase's coupling forces, then either hands off to autonomous
agents or drops into a guided walkthrough -- depending on risk.

Invocation: `/investigate "<rough goal + context>"`

Companion reference: `coupling-map.md` (Layer 1). It lists, per change axis, the files that
move in lockstep, the grill questions, the loose-end facts, the proof obligations, and the
routing flag. The scout reads it instead of re-crawling the codebase.

## State machine

```
/investigate "goal"
   |
   v
[0] VET  (inline, no agents yet)
   |   restate + classify the ask; consult AGENTS.md index + coupling-map FIRST;
   |   surface ambiguities and decision forks to the user BEFORE spawning anything;
   |   output = a pointed scout brief (targets, axes, known context)
   v
[1] SCOUT  (read-only agent)
   |   route the goal to the change axes it touches (see coupling-map.md);
   |   pull each axis's coupling set, grill questions, proof obligations, routing flag
   v
[2] GRILL  (back to the user)
   |   ask ONLY decisions the codebase cannot answer; never ask facts
   v
[3] RESOLVE LOOSE ENDS  (read-only agents)
   |   chase the facts the answers exposed; settle anything code can settle
   v
[4] READINESS GATE
   |-- not ready --------> back to [2]
   |-- ready + walkthrough-forced OR complex --> /checkpoint-walkthrough
   `-- ready + bounded ---------------------------> kick off execution agents
```

## [0] Vet (before any agent spends a token)

A primitive ask ("I wanna add X") gets scrutinized before discovery fans out,
so the path for discovery is solid and cheap:

1. Restate the ask in one sentence; classify it: new feature / enhancement /
   fix / might-already-exist. If might-already-exist, check first.
2. Consult the maps BEFORE exploring: AGENTS.md structural index +
   coupling-map.md axes. List candidate axes and pointed targets. Broad
   exploration is a last resort reserved for where the maps are silent --
   the maps exist so scouts start FROM them, not from a tree crawl.
3. Maintainer-domain or red-zone hit (social, netplay, music, libretrodroid/
   native)? Stop and surface to the user before any work proceeds.
4. Ambiguities and obvious decision forks in the ASK itself (not code facts)
   go to the user NOW, before scouting -- a scout brief built on a
   misreading wastes the whole pass.
5. Output: a narrowed scout brief -- named targets, candidate axes, known
   context (memory, recent commits), and the specific questions the scout
   must answer. No "map the whole subsystem" briefs when three files are
   already known.

## [1] Scout

Spawn one read-only scout agent with the VET brief (or do it inline for a narrow goal).
Determine which axes in `coupling-map.md` the goal touches. A goal usually spans more
than one axis (e.g. "add a setting that changes how saves restore" = SETTINGS +
SAVE-SYNC). Collect the union of: lockstep files, grill questions, proof obligations,
and the STRICTEST routing flag.

## [1b] Contact scoping (before any edit, not after)

The lockstep list says which files move. This says who reads what you are about to
change. Both run before the first edit; discovering contact afterwards is how a change
ships with two thirds of its consequences unreported.

For every symbol the plan will change, enumerate its readers first:

```
rg -n "symbolName\(" app/src/main
```

Four checks, each here because skipping it shipped a real defect:

1. **Widening a predicate is not a local change.** If a flag, filter or guard becomes
   more permissive, list every consumer and state what each one now does. Exhaustive is
   mandatory when any consumer performs a write or a delete: a permission flag flipped
   for one caller's benefit also widened a delete across every platform and doubled what
   a distribute-all wrote, and only the delete got reported.
2. **A list of literals is diffed against its registry before it is written.** Extensions,
   core ids, platform slugs, option tokens. `PlatformDefinitions` is the authority for what
   a rom file can be; a "junk file" extension list built from judgement put `png` on a
   delete path, and a PICO-8 cart is a png.
3. **Establish reachability before building a surface.** Ask who can arrive at it. A screen
   reached only through first run does not exist for any install that already completed it,
   and no amount of polish on that screen changes it.
4. **A guard added to one path is unfinished.** Enumerate every other path reaching the same
   mutation and say which are covered. Restated from AGENTS.md because it is the rule most
   often skipped under time pressure.

Search tooling: ripgrep is the source of truth. `find_usage` duplicates entries, counts the
declaration repeatedly, and reports inconsistent line numbers for the same symbol. Use it as
a hint, confirm with rg, never quote its counts.

## [2] Grill (interview-first)

Hard rule: ask DECISIONS, not FACTS.
- Decision = a fork the code cannot resolve (which dir wins, required-or-nullable, what
  behavior the setting changes). Ask it.
- Fact = anything discoverable (current DB version, which handler a platform uses, the
  nearest component to mirror). Go find it -- asking it is the failure mode that makes this
  skill a chore.

Draw questions from the matched axes' grill lists. Use AskUserQuestion for clean forks.
Do not produce a handoff brief until every coupling decision the goal touches is resolved.

## [3] Resolve loose ends

The answers expose new facts to verify. Spawn read-only agents to settle them. If an answer
reveals a NEW axis was touched, loop back to [2] for that axis's decisions.

## [4] Readiness gate (risk-flagged + override)

Ready = every grill question answered AND every loose end resolved AND proof obligations are
concrete. If not, return to [2].

Routing (auto, user may override either direction per run):
- Any matched axis flagged WALKTHROUGH-FORCED (save-sync, RomM) -> /checkpoint-walkthrough.
  These require live-data proof that cannot be satisfied by code-reading.
- Otherwise bounded UI / settings / platform work -> autonomous execution agents.
- Genuinely complex multi-axis work, even if eligible -> prefer /checkpoint-walkthrough.

State the routing and the reason; let the user override before proceeding.

## Handoff brief (what execution receives)

Whichever exit, emit a self-contained brief carrying:
1. Goal + resolved decisions (the grill answers).
2. The lockstep edit list (union of matched axes' coupling sets) -- every file that must move.
3. The contact set from [1b] -- every reader of every symbol the plan changes, and for each
   one whether the change alters what it does. A consumer that writes or deletes is named
   explicitly or the brief is not ready.
4. Proof obligations -- the verification the run must pass before claiming done, copied from
   the matched axes. This is what lets the user walk away: "done" means "proven", not "compiled".

Autonomous exit: kick off execution agents against the brief; they must report each proof
obligation as met/unmet, not just "finished".

Walkthrough exit: hand the brief to `/checkpoint-walkthrough` so each coupled change and its
proof is reviewed at a checkpoint.

## Notes

- This skill encodes Argosy's coupling. The pattern is portable; the map is not -- another
  project needs its own coupling-map.md.
- Keep coupling-map.md current. If a refactor moves a registry or adds an axis, update it
  there, not here.

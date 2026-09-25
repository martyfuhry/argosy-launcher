# RomM sync rules

Rules the RomM library sync keeps, and why. Code KDoc names what a declaration is. The
reasoning lives here.

## One game per RomM rom

Every RomM rom syncs as its own game. Regional copies RomM lists as siblings (a USA and a
Japan release of the same title) are separate library entries, and the Library region filter
narrows by region. Argosy used to merge siblings into one game. Play history, saves and
screenshots then reported under the winner's rom id whichever copy was played (issue #461), and
the merge hid the losing copies.

`SiblingSplitRepair` runs once, after the first clean library pass on a build without merging.
The pass gives every sibling rom its own game and moves its `game_files` rows there. The repair
then repoints what still refers to the merged game:

- a launch path that belongs to a sibling's file moves to that sibling, and the merged game
  falls back to its own downloaded file;
- one downloaded file claimed by rows of several games (two RomM roms with the same file name,
  such as a folder rom and a loose copy) stays with the oldest game, the one that downloaded it,
  and the split-off games release it;
- one file several games adopted by title during discovery, with no `game_files` row pointing at
  it, stays with the game whose rom file shares its name (the oldest game when none does), and
  the others release it;
- a sibling whose file is on disk but whose game has no launch path gets it relinked;
- remembered version selections that point at another game's file are cleared;
- `save_sync` rows keyed to a sibling's rom move to that sibling, with the region prefix the
  merge added to the channel name removed;
- `save_cache` and `state_cache` rows carry no rom id, so the channel prefix identifies them: a
  row moves to the one regional copy (same platform and IGDB id, or the owner a moved `save_sync`
  row named) whose region or `Version <rommId>` prefix it carries. A prefix two copies share
  identifies nothing and the row stays. A moved save cache row is never the active row;
- built-in saves kept under `saves/variants/<fileId>` are copied (never moved) into the owning
  game's save directory when nothing is there yet.

Play history the merge summed into the winner (playtime, play count, last played, sessions)
follows the saves. When every `save_sync` row the winner held moved to one sibling and none
stayed behind, the history moves to that sibling with them. Otherwise it stays on the winner:
nothing records which copy the play came from, and history split away from its saves would
point Recent at a game whose progress lives elsewhere. Favourites, ratings and status stay put.

## Game files record everything the server reports

`RomMGameFileSync` writes every file RomM reports for a rom: discs, updates, DLC and soundtrack
tracks. Rows are keyed by `rommFileId`, so a file stored under another game moves to the syncing
game with its local path. Which files a platform offers or downloads by default is decided in
the download and variant layers. Dropping references at sync time once left title-id platforms
with no soundtrack rows, so nothing could play a game's theme.

## A rom missing from a pass is not proof of deletion

`reconcileOrphans` turns "the server did not return this rom" into a mask change or a deletion.
Absence counts as deletion only when the server has also said the rom is not hidden from this
account. Without that statement (an older server, a failed call) nothing is deleted, because for
a restricted account absence and invisibility look identical and only one of the outcomes is
recoverable.

A rom the pages returned and the pass then set aside (a filter excluded it, a folder multi-disc
parent owns its discs) was decided against, and removing it is the point. A rom the pages never
mentioned is removable only once `GET /api/roms/identifiers` agrees it is gone. While the server
still lists it, absence means it moved platform or the pass failed on it, and the row stays.

## Account-scoped preference keys

`AccountScopedPreferenceKeys` lists the DataStore keys that follow the signed-in RomM account.
Membership is by key name, because the same name is declared in several repositories with
different value types. A key not listed stays device-global, so a key forgotten there degrades
to shared between accounts rather than silently empty for everyone.

These must stay device-global:

- `sync_filter_delete_orphans` decides whether a sync may delete rows from `games`, one shared
  row per rom with a CASCADE onto every account's overlay. A per-account copy let a new account
  read the `true` default and re-enable cleanup the first account had turned off.
- `secure_saves` picks one save mode for one shared save directory.
- `builtin_custom_save_path` and `builtin_custom_state_path` define the resolved save path, so a
  per-account value would make teardown and placement target different directories.
- The `active_session_*` keys are how an interrupted session is detected across a switch.
- The one-shot flags (`save_sync_local_rekey_done`, `save_path_cache_purged`,
  `sibling_split_repair_finished`, `builtin_migration_v2`, `last_integrity_check_time`,
  `emulator_update_last_check`, `first_run_complete`) are device migrations that run once per
  device. Re-running the rekey per account deletes save-sync rows.

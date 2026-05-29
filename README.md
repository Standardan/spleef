# Spleef

A complete **Spleef** minigame for Paper 1.21+ with a full match lifecycle: players queue
in a lobby, a countdown starts when enough join, everyone digs the floor out from under each
other, and the last player standing wins. The arena rebuilds itself between rounds.

## Download

**[Download the latest release »](https://github.com/Standardan/spleef/releases/latest)**

Drop the `.jar` into your server's `plugins/` folder and restart. Requires Paper 1.21+ (Java 21).

## How to play

- `/spleef join` — enter the queue (you're teleported to the lobby)
- `/spleef leave` — leave and get your stuff back
- When `min-players` have joined, a countdown begins; at zero the floor fills in and everyone
  gets a Spleef shovel. Dig blocks from under opponents; fall below the lose-line and you're out.

## Admin

| Command | Description |
|---|---|
| `/spleef setup lobby` | Set the waiting lobby to where you stand |
| `/spleef setup addspawn` | Add an in-game spawn point (run for each) |
| `/spleef setup floor1` / `floor2` | Set the two corners of the diggable floor |
| `/spleef setup losey` | Set the elimination height to your current Y |
| `/spleef start` / `/spleef stop` | Force-start or reset the match |

Permissions: `spleef.play` (default: all), `spleef.admin` (op).

## Design notes

- **Explicit state machine** — `GameState` is `WAITING → COUNTDOWN → PLAYING → ENDING → WAITING`.
  Every action checks the current state, so the game can't enter an illegal combination.
- **Elimination via a tick loop, not `PlayerMoveEvent`.** A repeating task checks each survivor's
  Y every half-second. `PlayerMoveEvent` fires many times per second per player — using it for
  this would be a needless performance drain. This is a deliberate, defensible choice.
- **Player-state snapshots.** `PlayerSnapshot` captures inventory, location, gamemode, food and
  health on join and restores them on exit — the minigame never destroys a player's survival state,
  even if the server stops mid-match (`onDisable` restores everyone).
- **Self-resetting arena.** The floor is a `Cuboid` that's refilled from scratch each round, so no
  expensive block-by-block snapshot/rollback is needed.

## Building

JDK 21 + Maven. `mvn clean package` → `target/spleef-1.0.0.jar`.

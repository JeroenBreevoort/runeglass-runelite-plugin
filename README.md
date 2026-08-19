# RuneGlass

A RuneLite plugin that syncs your own Old School RuneScape account — skills, items, loot and
progression — to the RuneGlass companion app.

The plugin is a sensor. It reads the local player's state, batches it, and posts it to the
RuneGlass backend over HTTPS. The app subscribes to that backend. The phone never talks to the
plugin directly.

```
RuneLite plugin  ──HTTPS──▶  Convex  ──WebSocket──▶  RuneGlass iOS app
   (this repo)                                        (pairing code entry)
```

## Requirements

**JDK 21.** Gradle 8.10 — which the wrapper pins, matching the RuneLite plugin template — cannot
run on JDK 24 or newer; it fails with `Unsupported class file major version`. The compile target
is still Java 11, as the Plugin Hub requires.

```sh
brew install openjdk@21
./scripts/local-setup.sh     # writes a gitignored gradle.properties pinning the daemon
```

## Running

```sh
./gradlew run                # launches RuneLite with RuneGlass loaded as a built-in plugin
```

To log in to the development client, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Developing against the mock backend

The real backend lives in a separate workspace. Until it exists, the mock server implements the
same contract so pairing and transport can be built and tested locally.

```sh
node tools/mock-server/server.js       # http://localhost:8787, no dependencies
```

Then set the plugin's **API base URL** setting to `http://localhost:8787` and click
**Link account** in the side panel. The mock prints the pairing code along with the exact `curl`
that stands in for the phone app claiming it.

`GET /dev/state` dumps everything the mock has received.

## Tests

```sh
./gradlew test
```

Unit tests cover the pairing state machine, the retryable/terminal error split, and the account
hash string conversion.

`ConvexCompatibilityTest` checks the wire format against Convex's documented limits — document
size, array length, nesting depth, and the rules for field names (the snapshot uses skill, quest
and diary names as keys). It runs without a backend and is what caught the bank sizing problem.

`PairingIntegrationTest` drives the real HTTP client against the mock, so the Java DTOs and the
mock's JSON field names are checked against each other rather than assumed to agree. It **skips
itself** when the mock isn't running, so `./gradlew test` stays green either way — start the mock
first if you want it to actually execute.

## Layout

| Path | What it is |
| --- | --- |
| `src/main/java/app/runeglass/plugin/RuneGlassApi.java` | **The wire contract.** Source of truth for the HTTP API. |
| `src/main/java/app/runeglass/plugin/RuneGlassPlugin.java` | Lifecycle, account identity tracking |
| `src/main/java/app/runeglass/plugin/RuneGlassConfig.java` | Settings, including the third-party opt-in |
| `src/main/java/app/runeglass/plugin/RuneGlassPanel.java` | Side panel |
| `tools/mock-server/server.js` | Local stand-in for the Convex HTTP actions |

`RuneGlassApi.java` and `tools/mock-server/server.js` mirror each other. Change them together, and
keep the Convex implementation in step.

## What gets collected

Each domain has its own toggle, and all of them are inert until the master **Enable sync** switch
is turned on.

| Domain | Source | Sent as an event | In snapshots |
| --- | --- | --- | --- |
| Skills | `StatChanged` | Level ups only | Experience for all 23 skills |
| Inventory | `ItemContainerChanged` | Never — far too noisy | Full contents |
| Equipment | `ItemContainerChanged` | On change | Full contents |
| Bank | `ItemContainerChanged` | Never — see below | Last known |
| Loot | `NpcLootReceived`, `LootReceived` | Every drop, with session kill count | — |
| Diaries | `VarbitChanged` | On completion | All 48 tiers |
| Combat achievements | `VarbitChanged` | On tier change | All 6 tiers |
| Quests | Polled per snapshot | On state transition | All quest states |

The bank is snapshot-only for a concrete reason: a full bank serializes to roughly 80 KB, and a
batch of a hundred such events is around 8 MiB — eight times Convex's 1 MiB document limit. A bank
change instead brings the next snapshot forward, so the contents travel once. `SyncService` also
enforces a byte budget per request, so no future event type can reintroduce the problem.

**Not yet implemented: the collection log.** Its contents are spread across thousands of
per-item varbits and are only fully readable while the log interface is open, so it needs its own
design rather than being bolted onto the varbit table. Everything else in the planned v1 scope is
in place.

Two deliberate exclusions:

- **PvP loot** (`LootRecordType.PLAYER`) is never collected — a kill record necessarily
  identifies the opponent.
- **`Skill.OVERALL`** is skipped, being derived from the other skills rather than earned.

## Things that will bite you

- **`accountHash` is a 64-bit `long`.** It travels as a *decimal string*, never a JSON number —
  JavaScript silently loses precision above 2^53, which would collide distinct accounts. The mock
  server rejects numeric account hashes so this can't regress unnoticed.
- **The config group is `runeglass` and cannot change.** Renaming a config group or key silently
  resets every user's saved settings, with no migration path.
- **`getAccountHash()` returns `-1` when logged out.** All collection is gated on a valid hash.
- **Bank contents are only readable while the bank is open**, so bank data is always "last known".

## Plugin Hub compliance

The Hub reviews for security and Jagex rule compliance, and states plainly that if compliance is
hard to verify, they will not merge. Constraints this repo is built around:

- Sync is **opt-in**, defaulting to off, carrying the Hub's mandated third-party warning verbatim.
- Only the **local player's own** data is ever transmitted. No party, clan, or nearby-player data.
- PvP loot is deliberately excluded — a kill record necessarily identifies another player, which
  brushes against the ban on crowdsourcing data about other players.
- No reflection, JNI, JNA, subprocesses, dynamic classloading, or Java serialization.
- No third-party Gradle dependencies. OkHttp, Gson and Guice all arrive via `runelite-client`.
- Java 11 target; `build.gradle` kept structurally identical to the upstream template.

## Licence

BSD-2-Clause. See [LICENSE](LICENSE).

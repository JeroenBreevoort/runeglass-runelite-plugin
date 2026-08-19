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

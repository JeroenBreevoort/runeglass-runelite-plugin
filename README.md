# RuneGlass

RuneGlass is an opt-in, read-only RuneLite plugin that syncs the logged-in
character's skills, XP, separately enabled quests, bird house and farming timers, and
an optional private character appearance snapshot
to the RuneGlass companion app.

The plugin uses a fixed RuneGlass HTTPS destination and never accepts a
user-configurable endpoint.

## Data and consent

The main sync toggle shows the data-sharing warning. Bird house timers, farming
timers, quests, and character appearance each have a separate toggle, all off by default,
with details in their descriptions and no repeated warning. Turning off the main
sync toggle stops all sharing.

Sync is disabled by default. After the user pairs RuneLite with an existing
RuneGlass character and enables sync, the plugin sends:

- character name, account mode, and profile type;
- complete skill levels and experience values; and
- plugin, RuneLite, and game revision metadata; and
- when separately enabled, current quest states by explicit quest ID and current
  quest points. No raw varbits or historical completion dates are sent; and
- when separately enabled, the semantic state and tier of the four bird house
  spaces observed on Fossil Island, with an estimated ready time only when the
  plugin observed the seeded transition; and
- when separately enabled, the semantic state, crop, and growth stage of all
  RuneLite time-tracking plant patches while their region is loaded, with an
  estimated ready time for actively growing crops. Compost bins are excluded.
- when separately enabled, the current character's equipped item identifiers,
  body colours, gender presentation, low-poly composite model geometry, and
  render attributes (face colours, transparency, render priorities, texture
  identifiers and a texture-presence flag). The snapshot is captured only while
  the local player is idle and replaces the previous snapshot.

RuneGlass also receives the IP address used for the HTTPS connection. The plugin
does not send raw varps, location history, other-player data, animations,
screenshots, game texture assets, chat, Jagex credentials, launcher sessions,
packets, or gameplay inputs.

Pairing uses a short-lived code entered in the signed-in RuneGlass app. The
resulting random, revocable connection credential cannot authenticate to
Jagex or read RuneGlass account data. It is stored atomically in an owner-only
local file derived from the matching RuneLite profile, so another logged-in
profile cannot reuse it and RuneLite's configuration logger never receives it.

Unsent snapshots are written atomically below RuneLite's own data directory and
retried in order. The queue is capped at 5 MiB and seven days. Disabling sync,
forgetting the client, or receiving a terminal authorization error clears the
credential and queued snapshots. The plugin never automates input or reads
other players, chat, launcher sessions, or Jagex credentials.

Timer observations use a separate in-memory latest-state retry path and are
never added to the durable skills queue. Turning off either timer sync drops its
unsent observation immediately. RuneGlass stores only the current semantic
bird house and farming patch state per character profile rather than an event or
location history.

Appearance observations use the same in-memory latest-state retry path. The
binary mesh is versioned, bounded to 512 KiB, and paired with explicit consent
metadata. Disabling appearance sync drops an unsent model immediately. RuneGlass
stores one current appearance per character profile; it does not retain model
history. Pausing appearance uploads does not delete the stored portrait.
Revoking its connection hides the portrait. Export or delete retained RuneLite
data in the RuneGlass app settings.

Quest observations use that in-memory retry path too. A stable login baseline
is followed by dirty, debounced scans at most once per 50 game ticks; unchanged
semantic bodies are not uploaded. No per-tick quest stream or completion history
is retained. Pause, logout, profile switch, forget-client and authorization failure
discard queued quest observations. Pausing retains the last server observation,
with its timestamp; revoking the connection hides it. The supported RuneLite pin
is 1.13.1 and quest catalog version 1 contains 213 released IDs.

In the companion app, manual quest declarations and pinned unlock goals are
separate from observed plugin facts. Deleting RuneLite data clears observed
quests but preserves manual planner data. Removing the character or deleting the
RuneGlass account clears both. Character export contains both sources separately.

## Development

The plugin requires Java 11.

```sh
./gradlew clean test
./gradlew run
```

For the RuneGlass **development backend**, run `npm run runelite:dev` from
the repository root. This compiles a separate development source set with the
fixed `small-cassowary-898` destination used by the mobile development app.
The normal `run` task uses production, even though RuneLite developer mode is
enabled. Never pair a production client with a development app.

Development credentials and queues are isolated under
`.runelite/runeglass-development`; production remains under `.runelite/runeglass`.
Each environment needs its own pairing. The development task does not copy
credentials, modify production sources, or affect the review/release JAR.
On macOS it includes RuneLite's required Java module opening. A locally available
JDK 17 may be selected with `JAVA_HOME` (compiled classes still target Java 11).

Only the user may perform in-game validation. Automated tools must not interact
with RuneScape.

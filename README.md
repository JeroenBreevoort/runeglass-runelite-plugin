# RuneGlass

RuneGlass is an opt-in, read-only RuneLite plugin that syncs the logged-in
character's skills, XP, and separately enabled bird house and farming timers
to the RuneGlass companion app.

The plugin uses a fixed RuneGlass HTTPS destination and never accepts a
user-configurable endpoint.

## Data and consent

Sync is disabled by default. After the user pairs RuneLite with an existing
RuneGlass character and enables sync, the plugin sends:

- character name, account mode, and profile type;
- complete skill levels and experience values; and
- plugin, RuneLite, and game revision metadata; and
- when separately enabled, the semantic state and tier of the four bird house
  spaces observed on Fossil Island, with an estimated ready time only when the
  plugin observed the seeded transition; and
- when separately enabled, the semantic state, crop, and growth stage of all
  RuneLite time-tracking plant patches while their region is loaded, with an
  estimated ready time for actively growing crops. Compost bins are excluded.

RuneGlass also receives the IP address used for the HTTPS connection. The plugin
does not send raw varps, location history, other-player data, chat, Jagex
credentials, launcher sessions, packets, or gameplay inputs.

Pairing uses a short-lived code entered in the signed-in RuneGlass app. The
resulting random, revocable `skills:write` credential cannot authenticate to
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

## Development

The plugin requires Java 11.

```sh
./gradlew clean test
./gradlew run
```

Only the user may perform in-game validation. Automated tools must not interact
with RuneScape.

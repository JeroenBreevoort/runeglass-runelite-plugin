# RuneGlass

RuneGlass is an opt-in, read-only RuneLite plugin that syncs the logged-in
character's skills and XP to the RuneGlass companion app.

The plugin uses a fixed RuneGlass HTTPS destination and never accepts a
user-configurable endpoint.

## Data and consent

Sync is disabled by default. After the user pairs RuneLite with an existing
RuneGlass character and enables sync, the plugin sends:

- character name, account mode, and profile type;
- complete skill levels and experience values; and
- plugin, RuneLite, and game revision metadata.

RuneGlass also receives the IP address used for the HTTPS connection. The plugin
does not collect other-player data, chat, Jagex credentials, launcher sessions,
packets, or gameplay inputs.

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

## Development

The plugin requires Java 11.

```sh
./gradlew clean test
./gradlew run
```

Only the user may perform in-game validation. Automated tools must not interact
with RuneScape.

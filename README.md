# RuneGlass

RuneGlass is an opt-in, read-only RuneLite plugin that syncs the logged-in
character's skills and XP to the RuneGlass companion app.

This repository contains a draft submitted for early Plugin Hub review.
Production transport and persistent credentials remain disabled until that
review is complete.

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
resulting scoped credential cannot authenticate to Jagex or read RuneGlass
account data.

## Development

The plugin requires Java 11.

```sh
./gradlew clean test
./gradlew run
```

Only the user may perform in-game validation. Automated tools must not interact
with RuneScape.

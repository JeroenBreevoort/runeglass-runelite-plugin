# RuneGlass

RuneGlass is an opt-in, read-only RuneLite plugin that syncs the logged-in
character's skills and XP to the RuneGlass companion app, along with quests,
bird house and farming timers, drop history, and a private character appearance
snapshot.

The plugin uses a fixed RuneGlass HTTPS destination and never accepts a
user-configurable endpoint.

## Data and consent

A single **Enable RuneGlass sync** toggle controls all sharing. It is off by
default, and enabling it shows RuneLite's third-party warning listing every data
category below; its description gives the details. There are no per-category
toggles. Turning it off stops all sharing, cancels requests and discards unsent
data.

After the user pairs RuneLite with an existing RuneGlass character and enables
sync, the plugin sends:

- character name, account mode, and profile type;
- complete skill levels and experience values; and
- plugin, RuneLite, and game revision metadata; and
- current quest states by explicit quest ID and current
  quest points. No raw varbits or historical completion dates are sent; and
- the semantic state and tier of the four bird house
  spaces observed on Fossil Island, with an estimated ready time only when the
  plugin observed the seeded transition; and
- the semantic state, crop, and growth stage of all
  RuneLite time-tracking plant patches while their region is loaded, with an
  estimated ready time for actively growing crops. Compost bins are excluded.
- supported drop sources, capture times, reward-roll counts, item IDs, names,
  quantities, available captured value estimates, and the count and
  collection-log evidence described below; and
- the current character's equipped item identifiers,
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
Jagex or read RuneGlass account data. It is stored atomically in a local file
derived from the matching RuneLite profile, so another logged-in
profile cannot reuse it and RuneLite's configuration logger never receives it.
New credential and queue files use owner-only permissions on macOS and Linux;
Windows access follows the user profile's filesystem permissions.

Storage uses RuneLite's Filepath API under `.runelite/plugin-data/runeglass`.
RuneLite automatically moves the previous `.runelite/runeglass` directory on
first startup, preserving pairings and queued data. Close other RuneLite clients
before upgrading: an older client still running can recreate the old directory,
which is not merged afterward. Rolling back to an older plugin does not move
the data back.

Unsent snapshots are written atomically below RuneLite's own data directory and
retried in order. The queue is capped at 5 MiB and seven days. Disabling sync,
forgetting the client, or receiving a terminal authorization error clears the
credential and queued snapshots. The plugin never automates input or reads
other players, private/player chat, launcher sessions, or Jagex credentials.
Game messages are examined locally only with the separate count-sharing opt-in;
the raw messages are never stored or uploaded.

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

## Drop history

Drop history is shared while sync is enabled and paired. Loot Tracker is a
required runtime prerequisite. On standard profiles, RuneGlass captures NPC and pickpocket
loot plus six clue tiers, Barrows, Chambers of Xeric, Theatre of Blood and Tombs
of Amascut. An event is kept only when at least one merged item stack has a
captured value of **50,000 GP or more**, or matches a curated notable reward for
that exact source type, source name and observed item ID. Distinct item stacks
are not added together to meet the threshold. Player loot, unknown/unreviewed
events and loot marked by Loot Tracker as originating from another plugin are
excluded. Coverage requires both plugins and consent to be active; it does not
reconstruct past drops or guarantee pet/collection-log coverage.

Each event sends capture time, source, reward-roll count, item IDs/names/quantities
and available frozen GE estimates. Reward-roll count is not kill count. Coins
have exact value; missing estimates remain unknown and untradeable rewards remain
untradeable. Qualifying events include all accompanying items, even low-value,
unknown-price or untradeable items. Low-only events are not queued or uploaded.
Raw event metadata is never uploaded. The app's current prices never change capture
eligibility; later price changes do not add or remove saved history. The app's
Drops and Favourites views use saved occurrences; a favourite is not a capture
rule. Existing pending queues and stored history are not rewritten by this policy.

Drops have a separate atomic disk queue: at most 5 MiB or 10,000 events per
connection, no age expiry, and no silent eviction. A full queue or disk failure
means new drops can be missed; the panel reports that gap. Uncommitted worker
captures (at most 256) are not guaranteed to survive a crash. Saved events retain
their IDs/content across logout/restart and retries. Invalid/rejected files remain
on disk, visibly flagged, while healthy records can continue.

Turning off sync cancels requests and deletes pending drop files for all local
profiles. Forget client and confirmed credential revocation delete
that connection's pending drops. A binding mismatch pauses and retains pending
history. A disk deletion failure is visible and blocks uploads. A changed consent
setting while the plugin is stopped is applied on its next start. Already synced
history remains in RuneGlass until deliberate RuneLite-data, character or account
deletion. Export and deletion are available in RuneGlass settings.

Drop uploads require RuneGlass server support. If the server temporarily pauses
uploads, saved drops remain queued and retry automatically. The plugin panel
shows whether events are waiting to sync.

## Development

The plugin requires Java 11.

```sh
./gradlew clean test
./gradlew run
```


JDK 17 can also build and run the plugin. Compiled classes target Java 11.

### Drop counts and collection-log evidence

Drops also carry the count shown in game with a supported reward and its
activity: boss kill counts, raid completions including mode, Barrows chests and
clue caskets, plus whether the collection log reported the item as new and
whether counts were missed between drops. Evidence is collected only while sync
and a paired capture session are active. Game messages are read locally only to
find this evidence and are never uploaded or stored. Collection-log chat
settings are recorded as enabled, disabled or unknown. Count coverage follows the
app's boss, raid-mode and clue-tier registry independently of the rate catalogue.
The backend selects prior recorded copies and assesses luck; the plugin never
computes a luck score. Missing or ambiguous counts remain absent.

Continuity persists across logout/restart in a profile-scoped file beside the
connection's drop queue. Turning off sync immediately stops collection, cancels
requests and deletes pending drops and continuity; forget/purge does the same for
its connection. Re-enabling starts a new continuity stream. The drop wire
`consentVersion: 1` and legacy fingerprints remain unchanged.

Low-value outcomes advance continuity without uploading their rewards. The correlation buffer normally
adds about six seconds before persistence and can lose pending observations on a
crash. See [drop-count testing](docs/development/drop-count-testing.md) for the
exact storage/evidence model, known source limits, upstream citations and the
required human in-game checklist. All rated and unrated catalogue items are
retained even without a price; regenerate both Java mirrors after catalogue or
counter changes with the root `npm run runelite:policy:generate` command.

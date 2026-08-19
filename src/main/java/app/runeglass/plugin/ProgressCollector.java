/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks long-running progression: quests, achievement diaries and combat achievement tiers.
 * <p>
 * Diaries and combat achievements are varbit-backed, so a change is detected the moment it
 * happens. Quests are spread across many varps with no single change signal, so they are polled
 * when a snapshot is taken and diffed against the last known state.
 * <p>
 * The varbit table is built from RuneLite's gameval constants. The legacy {@code Varbits} class
 * names these more conveniently but is deprecated, so the gameval names are used instead — they
 * were matched by constant value, not by guesswork.
 */
@Singleton
public class ProgressCollector
{
	/** Label to varbit id, for everything we watch. */
	private static final Map<String, Integer> TRACKED_VARBITS;
	/** Reverse lookup, so a VarbitChanged can be matched without scanning the table. */
	private static final Map<Integer, String> VARBIT_LABELS;

	static
	{
		final Map<String, Integer> tracked = new LinkedHashMap<>();

		// Achievement diaries: 12 regions x 4 tiers.

		tracked.put("ARDOUGNE_EASY", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE);
		tracked.put("ARDOUGNE_MEDIUM", VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE);
		tracked.put("ARDOUGNE_HARD", VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE);
		tracked.put("ARDOUGNE_ELITE", VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);

		tracked.put("DESERT_EASY", VarbitID.DESERT_DIARY_EASY_COMPLETE);
		tracked.put("DESERT_MEDIUM", VarbitID.DESERT_DIARY_MEDIUM_COMPLETE);
		tracked.put("DESERT_HARD", VarbitID.DESERT_DIARY_HARD_COMPLETE);
		tracked.put("DESERT_ELITE", VarbitID.DESERT_DIARY_ELITE_COMPLETE);

		tracked.put("FALADOR_EASY", VarbitID.FALADOR_DIARY_EASY_COMPLETE);
		tracked.put("FALADOR_MEDIUM", VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE);
		tracked.put("FALADOR_HARD", VarbitID.FALADOR_DIARY_HARD_COMPLETE);
		tracked.put("FALADOR_ELITE", VarbitID.FALADOR_DIARY_ELITE_COMPLETE);

		tracked.put("FREMENNIK_EASY", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE);
		tracked.put("FREMENNIK_MEDIUM", VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE);
		tracked.put("FREMENNIK_HARD", VarbitID.FREMENNIK_DIARY_HARD_COMPLETE);
		tracked.put("FREMENNIK_ELITE", VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);

		tracked.put("KANDARIN_EASY", VarbitID.KANDARIN_DIARY_EASY_COMPLETE);
		tracked.put("KANDARIN_MEDIUM", VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE);
		tracked.put("KANDARIN_HARD", VarbitID.KANDARIN_DIARY_HARD_COMPLETE);
		tracked.put("KANDARIN_ELITE", VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);

		tracked.put("KARAMJA_EASY", VarbitID.ATJUN_EASY_DONE);
		tracked.put("KARAMJA_MEDIUM", VarbitID.ATJUN_MED_DONE);
		tracked.put("KARAMJA_HARD", VarbitID.ATJUN_HARD_DONE);
		tracked.put("KARAMJA_ELITE", VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);

		tracked.put("KOUREND_EASY", VarbitID.KOUREND_DIARY_EASY_COMPLETE);
		tracked.put("KOUREND_MEDIUM", VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE);
		tracked.put("KOUREND_HARD", VarbitID.KOUREND_DIARY_HARD_COMPLETE);
		tracked.put("KOUREND_ELITE", VarbitID.KOUREND_DIARY_ELITE_COMPLETE);

		tracked.put("LUMBRIDGE_EASY", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE);
		tracked.put("LUMBRIDGE_MEDIUM", VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE);
		tracked.put("LUMBRIDGE_HARD", VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE);
		tracked.put("LUMBRIDGE_ELITE", VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);

		tracked.put("MORYTANIA_EASY", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE);
		tracked.put("MORYTANIA_MEDIUM", VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE);
		tracked.put("MORYTANIA_HARD", VarbitID.MORYTANIA_DIARY_HARD_COMPLETE);
		tracked.put("MORYTANIA_ELITE", VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);

		tracked.put("VARROCK_EASY", VarbitID.VARROCK_DIARY_EASY_COMPLETE);
		tracked.put("VARROCK_MEDIUM", VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE);
		tracked.put("VARROCK_HARD", VarbitID.VARROCK_DIARY_HARD_COMPLETE);
		tracked.put("VARROCK_ELITE", VarbitID.VARROCK_DIARY_ELITE_COMPLETE);

		tracked.put("WESTERN_EASY", VarbitID.WESTERN_DIARY_EASY_COMPLETE);
		tracked.put("WESTERN_MEDIUM", VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE);
		tracked.put("WESTERN_HARD", VarbitID.WESTERN_DIARY_HARD_COMPLETE);
		tracked.put("WESTERN_ELITE", VarbitID.WESTERN_DIARY_ELITE_COMPLETE);

		tracked.put("WILDERNESS_EASY", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE);
		tracked.put("WILDERNESS_MEDIUM", VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE);
		tracked.put("WILDERNESS_HARD", VarbitID.WILDERNESS_DIARY_HARD_COMPLETE);
		tracked.put("WILDERNESS_ELITE", VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);

		// Combat achievement tiers. The value is a completion status, not a boolean.
		tracked.put("CA_EASY", VarbitID.CA_TIER_STATUS_EASY);
		tracked.put("CA_MEDIUM", VarbitID.CA_TIER_STATUS_MEDIUM);
		tracked.put("CA_HARD", VarbitID.CA_TIER_STATUS_HARD);
		tracked.put("CA_ELITE", VarbitID.CA_TIER_STATUS_ELITE);
		tracked.put("CA_MASTER", VarbitID.CA_TIER_STATUS_MASTER);
		tracked.put("CA_GRANDMASTER", VarbitID.CA_TIER_STATUS_GRANDMASTER);

		TRACKED_VARBITS = Collections.unmodifiableMap(tracked);

		final Map<Integer, String> labels = new HashMap<>();
		tracked.forEach((label, id) -> labels.put(id, label));
		VARBIT_LABELS = Collections.unmodifiableMap(labels);
	}

	private final Client client;
	private final RuneGlassConfig config;
	private final SyncService sync;

	private volatile Map<String, Integer> lastVarbits = Collections.emptyMap();
	private volatile Map<String, String> lastQuests = Collections.emptyMap();

	@Inject
	ProgressCollector(Client client, RuneGlassConfig config, SyncService sync)
	{
		this.client = client;
		this.config = config;
		this.sync = sync;
	}

	public void reset()
	{
		lastVarbits = Collections.emptyMap();
		lastQuests = Collections.emptyMap();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!enabled())
		{
			return;
		}

		final String label = VARBIT_LABELS.get(event.getVarbitId());
		if (label == null)
		{
			return;
		}

		final Integer previous = lastVarbits.get(label);
		final int current = event.getValue();
		if (previous != null && previous == current)
		{
			return;
		}

		// Copy-on-write: the map is published to the snapshot builder on another thread.
		final Map<String, Integer> next = new LinkedHashMap<>(lastVarbits);
		next.put(label, current);
		lastVarbits = Collections.unmodifiableMap(next);

		// The first reading after login establishes a baseline rather than announcing progress.
		if (previous == null)
		{
			return;
		}

		final Map<String, Object> data = new HashMap<>();
		data.put("id", label);
		data.put("value", current);
		sync.record(RuneGlassApi.Kind.QUEST, data);
	}

	/**
	 * Re-reads everything and emits an event for each change. Must be called on the client thread;
	 * the plugin calls it just before building a snapshot.
	 */
	public void refresh()
	{
		if (!enabled() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		refreshVarbits();
		refreshQuests();
	}

	private void refreshVarbits()
	{
		final Map<String, Integer> current = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : TRACKED_VARBITS.entrySet())
		{
			current.put(entry.getKey(), client.getVarbitValue(entry.getValue()));
		}

		lastVarbits = Collections.unmodifiableMap(current);
	}

	private void refreshQuests()
	{
		final Map<String, String> current = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			final QuestState state = quest.getState(client);
			if (state != null)
			{
				current.put(quest.name(), state.name());
			}
		}

		final Map<String, String> previous = lastQuests;
		lastQuests = Collections.unmodifiableMap(current);

		// Nothing to diff against on the first pass of a session.
		if (previous.isEmpty())
		{
			return;
		}

		for (Map.Entry<String, String> entry : current.entrySet())
		{
			final String was = previous.get(entry.getKey());
			if (was == null || was.equals(entry.getValue()))
			{
				continue;
			}

			final Map<String, Object> data = new HashMap<>();
			data.put("quest", entry.getKey());
			data.put("state", entry.getValue());
			data.put("previousState", was);
			sync.record(RuneGlassApi.Kind.QUEST, data);
		}
	}

	private boolean enabled()
	{
		return config.syncEnabled() && config.syncProgress();
	}

	public Map<String, Integer> getVarbits()
	{
		return lastVarbits;
	}

	public Map<String, String> getQuests()
	{
		return lastQuests;
	}

	static int trackedVarbitCount()
	{
		return TRACKED_VARBITS.size();
	}
}

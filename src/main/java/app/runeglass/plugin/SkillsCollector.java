/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Watches skills. Level ups go out as events; experience totals ride along on snapshots.
 * <p>
 * {@code StatChanged} fires constantly — every experience drop — so emitting an event per change
 * would flood both the queue and the backend for no benefit. Only crossing a level boundary is
 * interesting enough to be its own event.
 */
@Singleton
public class SkillsCollector
{
	private final Client client;
	private final RuneGlassConfig config;
	private final SyncService sync;

	/**
	 * Last level seen per skill, or -1 when we have not seen that skill yet this session.
	 */
	private final int[] lastLevel = new int[Skill.values().length];

	@Inject
	SkillsCollector(Client client, RuneGlassConfig config, SyncService sync)
	{
		this.client = client;
		this.config = config;
		this.sync = sync;
		reset();
	}

	/**
	 * Forgets every baseline. Called when a character logs in, so levels are never compared across
	 * two different accounts.
	 */
	public void reset()
	{
		Arrays.fill(lastLevel, -1);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.syncEnabled() || !config.syncSkills())
		{
			return;
		}

		final Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			// Derived from the others; a total level change is not its own achievement.
			return;
		}

		final int index = skill.ordinal();
		final int previous = lastLevel[index];
		final int current = event.getLevel();
		lastLevel[index] = current;

		// On login the client replays every skill at its current level. Those first sightings
		// establish the baseline; treating them as level ups would announce 23 fake level ups.
		if (previous < 0 || current <= previous)
		{
			return;
		}

		final Map<String, Object> data = new HashMap<>();
		data.put("skill", skill.name());
		data.put("level", current);
		data.put("xp", event.getXp());

		sync.record(RuneGlassApi.Kind.LEVEL_UP, data);
	}

	/**
	 * Total experience per skill, for the snapshot. Must be called on the client thread.
	 */
	public Map<String, Integer> buildSkillXp()
	{
		final Map<String, Integer> xp = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			xp.put(skill.name(), client.getSkillExperience(skill));
		}
		return xp;
	}
}

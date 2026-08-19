/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Records loot from PvM sources, with a running kill count per source.
 * <p>
 * PvP loot is deliberately not collected. A kill record necessarily identifies the opponent, and
 * the Plugin Hub prohibits crowdsourcing data about other players. Excluding it keeps the plugin
 * unambiguously within the rules rather than relying on a reviewer's charity.
 */
@Singleton
public class LootCollector
{
	private final RuneGlassConfig config;
	private final SyncService sync;

	/** Kill count per source name, for the current session. */
	private final Map<String, Integer> killCounts = new HashMap<>();

	@Inject
	LootCollector(RuneGlassConfig config, SyncService sync)
	{
		this.config = config;
		this.sync = sync;
	}

	public void reset()
	{
		killCounts.clear();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!enabled())
		{
			return;
		}

		final NPC npc = event.getNpc();
		record(npc.getName(), LootRecordType.NPC.name(), npc.getId(), npc.getCombatLevel(), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!enabled())
		{
			return;
		}

		if (event.getType() == LootRecordType.PLAYER)
		{
			// PvP: identifies another player, so it never leaves the machine.
			return;
		}

		if (event.getType() == LootRecordType.NPC)
		{
			// Already captured via NpcLootReceived; recording both would double the kill count.
			return;
		}

		record(event.getName(), event.getType().name(), -1, event.getCombatLevel(), event.getItems());
	}

	private void record(String source, String type, int sourceId, int combatLevel, Collection<ItemStack> items)
	{
		if (source == null)
		{
			return;
		}

		final int kc = killCounts.merge(source, 1, Integer::sum);

		final List<RuneGlassApi.ItemStack> loot = new ArrayList<>();
		for (ItemStack stack : items)
		{
			loot.add(new RuneGlassApi.ItemStack(stack.getId(), stack.getQuantity(), -1));
		}

		final Map<String, Object> data = new HashMap<>();
		data.put("source", source);
		data.put("type", type);
		data.put("sessionKc", kc);
		data.put("items", loot);
		if (sourceId >= 0)
		{
			data.put("sourceId", sourceId);
		}
		if (combatLevel > 0)
		{
			data.put("combatLevel", combatLevel);
		}

		sync.record(RuneGlassApi.Kind.LOOT, data);
	}

	private boolean enabled()
	{
		return config.syncEnabled() && config.syncLoot();
	}

	/** Kill counts seen this session, for diagnostics and tests. */
	Map<String, Integer> getKillCounts()
	{
		return killCounts;
	}
}

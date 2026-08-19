/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class LootCollectorTest
{
	private SkillsCollectorTest.StubConfig config;
	private SkillsCollectorTest.RecordingSync sync;
	private LootCollector collector;

	@Before
	public void setUp()
	{
		config = new SkillsCollectorTest.StubConfig();
		sync = new SkillsCollectorTest.RecordingSync(config);
		collector = new LootCollector(config, sync);
	}

	/**
	 * The Plugin Hub prohibits crowdsourcing data about other players, and a PvP kill record
	 * necessarily identifies the opponent. This is the test that keeps that promise.
	 */
	@Test
	public void pvpLootNeverLeavesTheMachine()
	{
		lootReceived("SomeOtherPlayer", LootRecordType.PLAYER, new ItemStack(11802, 1));

		assertTrue("PvP loot identifies another player", sync.events.isEmpty());
		assertTrue(collector.getKillCounts().isEmpty());
	}

	@Test
	public void npcLootArrivingViaTheGenericEventIsIgnoredToAvoidDoubleCounting()
	{
		// NPC kills are captured through NpcLootReceived; counting both would double the kc.
		lootReceived("Vorkath", LootRecordType.NPC, new ItemStack(22006, 1));

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void chestAndEventLootIsRecorded()
	{
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));

		assertEquals(1, sync.events.size());
		final RuneGlassApi.Event event = sync.events.get(0);
		assertEquals(RuneGlassApi.Kind.LOOT, event.kind);
		assertEquals("Barrows", event.data.get("source"));
		assertEquals("EVENT", event.data.get("type"));
	}

	@Test
	public void pickpocketLootIsRecorded()
	{
		lootReceived("Master Farmer", LootRecordType.PICKPOCKET, new ItemStack(5318, 1));

		assertEquals(1, sync.events.size());
		assertEquals("PICKPOCKET", sync.events.get(0).data.get("type"));
	}

	@Test
	public void killCountAccumulatesPerSource()
	{
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4710, 1));
		lootReceived("Chambers of Xeric", LootRecordType.EVENT, new ItemStack(20997, 1));

		assertEquals(2, sync.events.get(1).data.get("sessionKc"));
		assertEquals("counts are per source, not global", 1, sync.events.get(2).data.get("sessionKc"));
	}

	@Test
	public void itemsAreCopiedIntoWireStacks()
	{
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 3), new ItemStack(995, 50_000));

		@SuppressWarnings("unchecked")
		final List<RuneGlassApi.ItemStack> items =
			(List<RuneGlassApi.ItemStack>) sync.events.get(0).data.get("items");

		assertEquals(2, items.size());
		assertEquals(4708, items.get(0).id);
		assertEquals(3, items.get(0).qty);
		assertEquals(995, items.get(1).id);
		assertEquals(50_000, items.get(1).qty);
	}

	@Test
	public void resetClearsKillCounts()
	{
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));
		collector.reset();
		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));

		assertEquals("a new session starts counting again", 1, sync.events.get(1).data.get("sessionKc"));
	}

	@Test
	public void nothingIsCollectedWhileTheLootToggleIsOff()
	{
		config.syncLoot = false;

		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void nothingIsCollectedWhileSyncIsDisabled()
	{
		config.syncEnabled = false;

		lootReceived("Barrows", LootRecordType.EVENT, new ItemStack(4708, 1));

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void lootWithNoSourceIsSkipped()
	{
		lootReceived(null, LootRecordType.EVENT, new ItemStack(4708, 1));

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void emptyLootStillRecordsTheKill()
	{
		lootReceived("Barrows", LootRecordType.EVENT);

		assertEquals("a dry kill is still a kill", 1, sync.events.size());
		assertEquals(1, sync.events.get(0).data.get("sessionKc"));
	}

	private void lootReceived(String name, LootRecordType type, ItemStack... items)
	{
		collector.onLootReceived(new LootReceived(
			name, 0, type, items.length == 0 ? Collections.emptyList() : Arrays.asList(items), 1, null));
	}
}

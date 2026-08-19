/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SkillsCollectorTest
{
	private StubConfig config;
	private RecordingSync sync;
	private SkillsCollector collector;

	@Before
	public void setUp()
	{
		config = new StubConfig();
		sync = new RecordingSync(config);
		// The client is only touched by buildSkillXp(), which these tests do not exercise.
		collector = new SkillsCollector(null, config, sync);
	}

	@Test
	public void theFirstSightingOfASkillIsABaselineNotALevelUp()
	{
		// On login the client replays every skill at its current level.
		statChanged(Skill.SLAYER, 87, 4_000_000);

		assertTrue("logging in must not announce level ups", sync.events.isEmpty());
	}

	@Test
	public void crossingALevelBoundaryEmitsOneEvent()
	{
		statChanged(Skill.SLAYER, 87, 4_000_000);
		statChanged(Skill.SLAYER, 88, 4_400_000);

		assertEquals(1, sync.events.size());
		assertEquals(RuneGlassApi.Kind.LEVEL_UP, sync.events.get(0).kind);
		assertEquals("SLAYER", sync.events.get(0).data.get("skill"));
		assertEquals(88, sync.events.get(0).data.get("level"));
		assertEquals(4_400_000, sync.events.get(0).data.get("xp"));
	}

	@Test
	public void experienceGainedWithinALevelIsNotAnEvent()
	{
		statChanged(Skill.SLAYER, 87, 4_000_000);
		statChanged(Skill.SLAYER, 87, 4_100_000);
		statChanged(Skill.SLAYER, 87, 4_200_000);

		assertTrue("StatChanged fires on every xp drop; only levels are events", sync.events.isEmpty());
	}

	@Test
	public void overallIsIgnoredBecauseItIsDerived()
	{
		statChanged(Skill.OVERALL, 1500, 100_000_000);
		statChanged(Skill.OVERALL, 1501, 100_100_000);

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void aDroppedLevelIsNotReportedAsALevelUp()
	{
		// Boosts are reported via getBoostedLevel, but guard the ordering anyway.
		statChanged(Skill.ATTACK, 80, 2_000_000);
		statChanged(Skill.ATTACK, 78, 2_000_000);

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void skillsAreTrackedIndependently()
	{
		statChanged(Skill.SLAYER, 87, 4_000_000);
		statChanged(Skill.MINING, 70, 800_000);
		statChanged(Skill.MINING, 71, 850_000);

		assertEquals(1, sync.events.size());
		assertEquals("MINING", sync.events.get(0).data.get("skill"));
	}

	@Test
	public void resetForgetsBaselinesSoAnotherCharacterStartsClean()
	{
		statChanged(Skill.SLAYER, 87, 4_000_000);
		collector.reset();

		// An alt at a lower level must not read as a level change in either direction.
		statChanged(Skill.SLAYER, 40, 40_000);
		statChanged(Skill.SLAYER, 41, 45_000);

		assertEquals("first sighting after reset is a baseline again", 1, sync.events.size());
		assertEquals(41, sync.events.get(0).data.get("level"));
	}

	@Test
	public void nothingIsCollectedWhileSyncIsDisabled()
	{
		config.syncEnabled = false;

		statChanged(Skill.SLAYER, 87, 4_000_000);
		statChanged(Skill.SLAYER, 88, 4_400_000);

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void nothingIsCollectedWhileTheSkillsToggleIsOff()
	{
		config.syncSkills = false;

		statChanged(Skill.SLAYER, 87, 4_000_000);
		statChanged(Skill.SLAYER, 88, 4_400_000);

		assertTrue(sync.events.isEmpty());
	}

	private void statChanged(Skill skill, int level, int xp)
	{
		collector.onStatChanged(new StatChanged(skill, xp, level, level));
	}

	// ------------------------------------------------------------------

	static final class RecordingSync extends SyncService
	{
		final List<RuneGlassApi.Event> events = new ArrayList<>();

		RecordingSync(RuneGlassConfig config)
		{
			super(null, config, null);
		}

		@Override
		public void record(String kind, Map<String, Object> data)
		{
			events.add(new RuneGlassApi.Event(events.size(), 0L, kind, data));
		}
	}

	static class StubConfig implements RuneGlassConfig
	{
		boolean syncEnabled = true;
		boolean syncSkills = true;
		boolean syncItems = true;

		@Override
		public boolean syncEnabled()
		{
			return syncEnabled;
		}

		@Override
		public boolean syncSkills()
		{
			return syncSkills;
		}

		@Override
		public boolean syncItems()
		{
			return syncItems;
		}

		@Override
		public void deviceToken(String value)
		{
		}

		@Override
		public void linkedAccountName(String value)
		{
		}
	}
}

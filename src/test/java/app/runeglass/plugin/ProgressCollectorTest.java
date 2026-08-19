/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ProgressCollectorTest
{
	private SkillsCollectorTest.StubConfig config;
	private SkillsCollectorTest.RecordingSync sync;
	private ProgressCollector collector;

	@Before
	public void setUp()
	{
		config = new SkillsCollectorTest.StubConfig();
		sync = new SkillsCollectorTest.RecordingSync(config);
		// The client is only used by refresh(), which these tests do not exercise.
		collector = new ProgressCollector(null, config, sync);
	}

	@Test
	public void tracksEveryDiaryTierAndCombatAchievementTier()
	{
		// 12 diary regions x 4 tiers, plus 6 combat achievement tiers.
		assertEquals(54, ProgressCollector.trackedVarbitCount());
	}

	@Test
	public void theFirstReadingOfAVarbitIsABaselineNotAnAchievement()
	{
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);

		assertTrue("logging in must not replay every completed diary", sync.events.isEmpty());
		assertEquals(Integer.valueOf(1), collector.getVarbits().get("VARROCK_EASY"));
	}

	@Test
	public void completingADiaryAfterTheBaselineEmitsAnEvent()
	{
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 0);
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);

		assertEquals(1, sync.events.size());
		assertEquals("VARROCK_EASY", sync.events.get(0).data.get("id"));
		assertEquals(1, sync.events.get(0).data.get("value"));
	}

	@Test
	public void anUnchangedValueIsNotReemitted()
	{
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 0);
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);

		assertEquals(1, sync.events.size());
	}

	@Test
	public void combatAchievementTiersAreTracked()
	{
		varbitChanged(VarbitID.CA_TIER_STATUS_HARD, 0);
		varbitChanged(VarbitID.CA_TIER_STATUS_HARD, 2);

		assertEquals(1, sync.events.size());
		assertEquals("CA_HARD", sync.events.get(0).data.get("id"));
	}

	@Test
	public void unrelatedVarbitsAreIgnored()
	{
		// The client fires VarbitChanged constantly; only the tracked table matters.
		varbitChanged(1, 1);
		varbitChanged(9999, 5);

		assertTrue(sync.events.isEmpty());
		assertTrue(collector.getVarbits().isEmpty());
	}

	@Test
	public void resetClearsBaselinesSoAnotherCharacterStartsClean()
	{
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);
		collector.reset();

		// An alt with the diary incomplete must not read as having lost it.
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 0);

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void publishedVarbitMapIsImmutable()
	{
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);

		try
		{
			collector.getVarbits().put("HACK", 1);
			org.junit.Assert.fail("the map is read from another thread and must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	@Test
	public void nothingIsCollectedWhileTheProgressToggleIsOff()
	{
		config.syncProgress = false;

		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 0);
		varbitChanged(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);

		assertTrue(sync.events.isEmpty());
		assertTrue(collector.getVarbits().isEmpty());
	}

	private void varbitChanged(int varbitId, int value)
	{
		final VarbitChanged event = new VarbitChanged();
		event.setVarbitId(varbitId);
		event.setValue(value);
		collector.onVarbitChanged(event);
	}
}

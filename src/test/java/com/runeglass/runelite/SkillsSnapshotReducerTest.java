package com.runeglass.runelite;

import java.time.Instant;
import java.util.Optional;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillsSnapshotReducerTest
{
	private static final Instant OBSERVED_AT = Instant.parse("2026-08-24T10:15:29Z");

	@Test
	public void emitsOnlyCompleteDirtySnapshotsInTheFixedCatalogOrder()
	{
		SkillsSnapshotReducer reducer = new SkillsSnapshotReducer();

		for (int index = 0; index < SkillCatalog.trainableSkills().size() - 1; index++)
		{
			Skill skill = SkillCatalog.trainableSkills().get(index);
			reducer.accept(skill, (index + 1) * 100, index + 1, index + 2);
		}

		assertFalse(reducer.isComplete());
		assertFalse(reducer.takeSnapshot(OBSERVED_AT, SnapshotReason.LOGIN_BASELINE).isPresent());

		Skill sailing = SkillCatalog.trainableSkills().get(23);
		reducer.accept(sailing, 2_400, 24, 25);
		SkillsSnapshot snapshot = reducer
			.takeSnapshot(OBSERVED_AT, SnapshotReason.LOGIN_BASELINE)
			.orElseThrow(AssertionError::new);

		long[] expectedExperience = new long[25];
		int[] expectedLevels = new int[25];
		int[] expectedBoostedLevels = new int[25];
		for (int index = 1; index < 25; index++)
		{
			expectedExperience[index] = index * 100L;
			expectedLevels[index] = index;
			expectedBoostedLevels[index] = index + 1;
			expectedExperience[0] += expectedExperience[index];
			expectedLevels[0] += expectedLevels[index];
			expectedBoostedLevels[0] += expectedBoostedLevels[index];
		}

		assertEquals(SkillsSnapshot.TYPE, snapshot.getType());
		assertEquals(OBSERVED_AT.toString(), snapshot.getObservedAt());
		assertEquals("login_baseline", snapshot.getReason());
		assertEquals(1, snapshot.getCatalogVersion());
		assertArrayEquals(expectedExperience, snapshot.getExperience());
		assertArrayEquals(expectedLevels, snapshot.getLevels());
		assertArrayEquals(expectedBoostedLevels, snapshot.getBoostedLevels());
		assertFalse(reducer.hasChanges());
		assertFalse(reducer.takeSnapshot(OBSERVED_AT, SnapshotReason.CHANGE_CHECKPOINT).isPresent());

		reducer.accept(Skill.ATTACK, 100, 1, 2);
		assertFalse(reducer.hasChanges());
		reducer.accept(Skill.ATTACK, 101, 1, 2);
		assertTrue(reducer.hasChanges());
	}

	@Test
	public void snapshotsDefensivelyCopyTheirVectors()
	{
		SkillsSnapshotReducer reducer = completeReducer();
		SkillsSnapshot snapshot = reducer
			.takeSnapshot(OBSERVED_AT, SnapshotReason.LOGIN_BASELINE)
			.orElseThrow(AssertionError::new);

		long[] experience = snapshot.getExperience();
		experience[1] = 99_999;

		assertEquals(100L, snapshot.getExperience()[1]);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOutOfRangeExperience()
	{
		new SkillsSnapshotReducer().accept(Skill.ATTACK, 200_000_001, 99, 99);
	}

	private static SkillsSnapshotReducer completeReducer()
	{
		SkillsSnapshotReducer reducer = new SkillsSnapshotReducer();
		for (int index = 0; index < SkillCatalog.trainableSkills().size(); index++)
		{
			reducer.accept(SkillCatalog.trainableSkills().get(index), (index + 1) * 100, index + 1, index + 1);
		}
		return reducer;
	}
}

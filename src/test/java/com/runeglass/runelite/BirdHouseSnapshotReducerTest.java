package com.runeglass.runelite;

import java.time.Instant;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BirdHouseSnapshotReducerTest
{
	private static final Instant OBSERVED_AT = Instant.parse("2026-08-28T10:00:00Z");

	@Test
	public void emitsOnlySemanticStateForAllFourSpaces()
	{
		BirdHouseSnapshotReducer reducer = new BirdHouseSnapshotReducer();
		int[] states = new int[]{0, 20, 27, 24};
		reducer.observe(14650, 0, states, OBSERVED_AT);
		BirdHouseSnapshot snapshot = reducer
			.observe(14650, 0, states, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);

		assertEquals(4, snapshot.getHouses().size());
		assertEquals("meadow_north", snapshot.getHouses().get(0).getSpace());
		assertEquals("empty", snapshot.getHouses().get(0).getState());
		assertNull(snapshot.getHouses().get(0).getTier());
		assertEquals("built", snapshot.getHouses().get(1).getState());
		assertEquals("yew", snapshot.getHouses().get(1).getTier());
		assertEquals("seeded", snapshot.getHouses().get(2).getState());
		assertEquals("redwood", snapshot.getHouses().get(2).getTier());
		assertNull(snapshot.getHouses().get(2).getReadyAt());
	}

	@Test
	public void estimatesReadinessOnlyWhenTheSeededTransitionWasObserved()
	{
		BirdHouseSnapshotReducer reducer = new BirdHouseSnapshotReducer();
		reducer.observe(14650, 0, new int[]{0, 20, 26, 23}, OBSERVED_AT);
		reducer.observe(14650, 0, new int[]{0, 20, 26, 23}, OBSERVED_AT.plusSeconds(1));

		Instant seededAt = OBSERVED_AT.plusSeconds(10);
		BirdHouseSnapshot snapshot = reducer
			.observe(14650, 0, new int[]{0, 20, 27, 24}, seededAt)
			.orElseThrow(AssertionError::new);

		assertEquals(seededAt.toString(), snapshot.getHouses().get(2).getStartedAt());
		assertEquals(
			seededAt.plusSeconds(50 * 60).toString(),
			snapshot.getHouses().get(2).getReadyAt());
		assertEquals(seededAt.toString(), snapshot.getHouses().get(3).getStartedAt());
	}

	@Test
	public void ignoresUnchangedOffIslandAndLikelyUninitializedObservations()
	{
		BirdHouseSnapshotReducer reducer = new BirdHouseSnapshotReducer();
		int[] built = new int[]{20, 20, 20, 20};
		assertFalse(reducer.observe(12850, 0, built, OBSERVED_AT).isPresent());
		assertFalse(reducer.observe(14650, 0, built, OBSERVED_AT).isPresent());
		assertTrue(reducer.observe(14650, 0, built, OBSERVED_AT.plusSeconds(1)).isPresent());
		assertFalse(reducer.observe(14650, 0, built, OBSERVED_AT.plusSeconds(2)).isPresent());
		assertFalse(reducer.observe(
			14650,
			0,
			new int[]{0, 0, 0, 0},
			OBSERVED_AT.plusSeconds(3)).isPresent());
	}

	@Test
	public void requiresAStableInitialObservationBeforePublishing()
	{
		BirdHouseSnapshotReducer reducer = new BirdHouseSnapshotReducer();
		assertFalse(reducer.observe(
			14650,
			0,
			new int[]{16, 16, 16, 16},
			OBSERVED_AT).isPresent());
		assertFalse(reducer.observe(
			14650,
			0,
			new int[]{15, 15, 15, 15},
			OBSERVED_AT.plusSeconds(1)).isPresent());

		BirdHouseSnapshot snapshot = reducer.observe(
			14650,
			0,
			new int[]{15, 15, 15, 15},
			OBSERVED_AT.plusSeconds(2))
			.orElseThrow(AssertionError::new);
		for (BirdHouseObservation house : snapshot.getHouses())
		{
			assertEquals("seeded", house.getState());
			assertNull(house.getStartedAt());
			assertNull(house.getReadyAt());
		}
	}

	@Test
	public void rejectsUnknownVarpsWithoutReplacingTheLastValidState()
	{
		BirdHouseSnapshotReducer reducer = new BirdHouseSnapshotReducer();
		reducer.observe(14650, 0, new int[]{20, 20, 20, 20}, OBSERVED_AT);
		reducer.observe(14650, 0, new int[]{20, 20, 20, 20}, OBSERVED_AT.plusSeconds(1));
		Optional<BirdHouseSnapshot> invalid = reducer.observe(
			14650,
			0,
			new int[]{28, 20, 20, 20},
			OBSERVED_AT.plusSeconds(2));

		assertFalse(invalid.isPresent());
		assertFalse(reducer.observe(
			14650,
			0,
			new int[]{20, 20, 20, 20},
			OBSERVED_AT.plusSeconds(3)).isPresent());
	}
}

package com.runeglass.runelite;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class BirdHouseSnapshotReducer
{
	private static final int MAX_VARP = BirdHouseTier.values().length * 3;
	private static final Duration READY_AFTER = Duration.ofMinutes(50);
	private static final Set<Integer> FOSSIL_ISLAND_REGIONS = Set.of(
		14650,
		14651,
		14652,
		14906,
		14907,
		15162,
		15163);

	private final Map<BirdHouseSpace, StoredObservation> observations =
		new EnumMap<>(BirdHouseSpace.class);
	private int[] initialCandidate;

	Optional<BirdHouseSnapshot> observe(
		int regionId,
		int plane,
		int[] varps,
		Instant observedAt)
	{
		if (!FOSSIL_ISLAND_REGIONS.contains(regionId) || plane != 0)
		{
			return Optional.empty();
		}
		if (varps == null || varps.length != BirdHouseSpace.values().length)
		{
			throw new IllegalArgumentException("Expected four bird house varps");
		}
		for (int varp : varps)
		{
			if (varp < 0 || varp > MAX_VARP)
			{
				return Optional.empty();
			}
		}
		if (observations.isEmpty())
		{
			if (!Arrays.equals(initialCandidate, varps))
			{
				initialCandidate = varps.clone();
				return Optional.empty();
			}
			initialCandidate = null;
		}

		int removalCount = 0;
		for (int index = 0; index < varps.length; index++)
		{
			StoredObservation previous = observations.get(BirdHouseSpace.values()[index]);
			if (previous != null && previous.varp > 0 && varps[index] == 0)
			{
				removalCount++;
			}
		}
		if (removalCount > 2)
		{
			return Optional.empty();
		}

		boolean changed = observations.isEmpty();
		Map<BirdHouseSpace, StoredObservation> next =
			new EnumMap<>(BirdHouseSpace.class);
		List<BirdHouseObservation> semantic = new ArrayList<>();
		for (int index = 0; index < varps.length; index++)
		{
			BirdHouseSpace space = BirdHouseSpace.values()[index];
			int varp = varps[index];
			BirdHouseState state = BirdHouseState.fromVarp(varp);
			BirdHouseTier tier = varp == 0 ? null : BirdHouseTier.fromVarp(varp);
			StoredObservation previous = observations.get(space);
			Instant startedAt = null;
			if (state == BirdHouseState.SEEDED)
			{
				if (previous != null
					&& previous.state == BirdHouseState.SEEDED
					&& previous.tier == tier)
				{
					startedAt = previous.startedAt;
				}
				else if (previous != null)
				{
					startedAt = observedAt;
				}
			}
			StoredObservation current = new StoredObservation(
				varp,
				state,
				tier,
				startedAt);
			next.put(space, current);
			changed = changed || !current.sameSemanticValue(previous);
			semantic.add(new BirdHouseObservation(
				space,
				state,
				tier,
				startedAt,
				startedAt == null ? null : startedAt.plus(READY_AFTER)));
		}

		observations.clear();
		observations.putAll(next);
		return changed
			? Optional.of(new BirdHouseSnapshot(observedAt, semantic))
			: Optional.empty();
	}

	void reset()
	{
		observations.clear();
		initialCandidate = null;
	}

	private static final class StoredObservation
	{
		private final int varp;
		private final BirdHouseState state;
		private final BirdHouseTier tier;
		private final Instant startedAt;

		private StoredObservation(
			int varp,
			BirdHouseState state,
			BirdHouseTier tier,
			Instant startedAt)
		{
			this.varp = varp;
			this.state = state;
			this.tier = tier;
			this.startedAt = startedAt;
		}

		private boolean sameSemanticValue(StoredObservation other)
		{
			return other != null
				&& state == other.state
				&& tier == other.tier
				&& java.util.Objects.equals(startedAt, other.startedAt);
		}
	}
}

package com.runeglass.runelite;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class FarmingPatchSnapshotReducer
{
	private final Map<FarmingPatchLocation, Integer> initialCandidates =
		new EnumMap<>(FarmingPatchLocation.class);
	private final Map<FarmingPatchLocation, FarmingPatchDecoder.DecodedState> observations =
		new EnumMap<>(FarmingPatchLocation.class);

	Optional<FarmingPatchObservation> observe(
		FarmingPatchLocation location,
		int varbitValue,
		Instant observedAt)
	{
		FarmingPatchDecoder.DecodedState decoded = FarmingPatchDecoder.decode(
			location.getPatchType(),
			varbitValue);
		if (decoded == null)
		{
			return Optional.empty();
		}
		if (!observations.containsKey(location))
		{
			Integer candidate = initialCandidates.put(location, varbitValue);
			if (candidate == null || candidate != varbitValue)
			{
				return Optional.empty();
			}
			initialCandidates.remove(location);
		}

		FarmingPatchDecoder.DecodedState previous = observations.put(location, decoded);
		if (decoded.sameSemanticValue(previous))
		{
			return Optional.empty();
		}
		Instant readyAt = decoded.state == FarmingPatchState.GROWING
			? observedAt.plus(decoded.crop.getGrowthTick().multipliedBy(
				decoded.stages - 1L - decoded.stage))
			: null;
		return Optional.of(new FarmingPatchObservation(
			location,
			decoded.state,
			decoded.crop,
			decoded.stage,
			decoded.stages,
			readyAt));
	}

	void reset()
	{
		initialCandidates.clear();
		observations.clear();
	}
}

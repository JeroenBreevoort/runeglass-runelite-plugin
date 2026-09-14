package com.runeglass.runelite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class FarmingPatchSnapshot
{
	private final String snapshotId;
	private final Instant observedAt;
	private final List<FarmingPatchObservation> patches;

	FarmingPatchSnapshot(Instant observedAt, List<FarmingPatchObservation> patches)
	{
		this(UUID.randomUUID().toString(), observedAt, patches);
	}

	FarmingPatchSnapshot(
		String snapshotId,
		Instant observedAt,
		List<FarmingPatchObservation> patches)
	{
		this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
		Objects.requireNonNull(patches, "patches");
		if (patches.isEmpty() || patches.size() > 16)
		{
			throw new IllegalArgumentException("patches must contain between 1 and 8 observations");
		}
		this.patches = Collections.unmodifiableList(new ArrayList<>(patches));
	}

	String getSnapshotId()
	{
		return snapshotId;
	}

	String getObservedAt()
	{
		return observedAt.toString();
	}

	List<FarmingPatchObservation> getPatches()
	{
		return patches;
	}
}

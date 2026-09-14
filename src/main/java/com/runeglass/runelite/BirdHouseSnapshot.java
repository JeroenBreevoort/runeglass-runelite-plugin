package com.runeglass.runelite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class BirdHouseSnapshot
{
	private final String snapshotId;
	private final String observedAt;
	private final List<BirdHouseObservation> houses;

	BirdHouseSnapshot(Instant observedAt, List<BirdHouseObservation> houses)
	{
		this(UUID.randomUUID().toString(), observedAt, houses);
	}

	BirdHouseSnapshot(
		String snapshotId,
		Instant observedAt,
		List<BirdHouseObservation> houses)
	{
		this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt").toString();
		if (houses.size() != BirdHouseSpace.values().length)
		{
			throw new IllegalArgumentException("A bird house snapshot requires four spaces");
		}
		this.houses = Collections.unmodifiableList(new ArrayList<>(houses));
	}

	String getSnapshotId()
	{
		return snapshotId;
	}

	String getObservedAt()
	{
		return observedAt;
	}

	List<BirdHouseObservation> getHouses()
	{
		return houses;
	}
}

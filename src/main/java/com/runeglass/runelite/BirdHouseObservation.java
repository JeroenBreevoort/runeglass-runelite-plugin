package com.runeglass.runelite;

import java.time.Instant;
import java.util.Objects;

final class BirdHouseObservation
{
	private final String space;
	private final String state;
	private final String tier;
	private final String startedAt;
	private final String readyAt;

	BirdHouseObservation(
		BirdHouseSpace space,
		BirdHouseState state,
		BirdHouseTier tier,
		Instant startedAt,
		Instant readyAt)
	{
		this.space = Objects.requireNonNull(space, "space").wireValue();
		this.state = Objects.requireNonNull(state, "state").wireValue();
		this.tier = tier == null ? null : tier.wireValue();
		this.startedAt = startedAt == null ? null : startedAt.toString();
		this.readyAt = readyAt == null ? null : readyAt.toString();
	}

	String getSpace()
	{
		return space;
	}

	String getState()
	{
		return state;
	}

	String getTier()
	{
		return tier;
	}

	String getStartedAt()
	{
		return startedAt;
	}

	String getReadyAt()
	{
		return readyAt;
	}
}

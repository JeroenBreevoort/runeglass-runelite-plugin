package com.runeglass.runelite;

import java.time.Instant;

final class FarmingPatchObservation
{
	private final String patchType;
	private final String location;
	private final String state;
	private final String crop;
	private final Integer stage;
	private final Integer stages;
	private final String readyAt;
	private final Boolean estimated;

	FarmingPatchObservation(
		FarmingPatchLocation location,
		FarmingPatchState state,
		FarmingCrop crop,
		Integer stage,
		Integer stages,
		Instant readyAt)
	{
		this.patchType = location.getPatchType().getWireName();
		this.location = location.getWireName();
		this.state = state.getWireName();
		this.crop = crop == null ? null : crop.getWireName();
		this.stage = stage;
		this.stages = stages;
		this.readyAt = readyAt == null ? null : readyAt.toString();
		this.estimated = readyAt == null ? null : Boolean.TRUE;
	}

	String getPatchType()
	{
		return patchType;
	}

	String getLocation()
	{
		return location;
	}

	String getState()
	{
		return state;
	}

	String getCrop()
	{
		return crop;
	}

	Integer getStage()
	{
		return stage;
	}

	Integer getStages()
	{
		return stages;
	}

	String getReadyAt()
	{
		return readyAt;
	}

	Boolean getEstimated()
	{
		return estimated;
	}
}

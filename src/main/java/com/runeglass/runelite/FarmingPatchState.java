package com.runeglass.runelite;

enum FarmingPatchState
{
	EMPTY("empty"),
	GROWING("growing"),
	HARVESTABLE("harvestable"),
	DISEASED("diseased"),
	DEAD("dead");

	private final String wireName;

	FarmingPatchState(String wireName)
	{
		this.wireName = wireName;
	}

	String getWireName()
	{
		return wireName;
	}
}

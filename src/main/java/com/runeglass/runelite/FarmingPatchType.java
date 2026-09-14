package com.runeglass.runelite;

import java.time.Duration;

enum FarmingPatchType
{
	ALLOTMENT("allotment", Duration.ofMinutes(10)),
	FLOWER("flower", Duration.ofMinutes(5)),
	HERB("herb", Duration.ofMinutes(20)),
	TREE("tree", Duration.ofMinutes(40)),
	FRUIT_TREE("fruit_tree", Duration.ofMinutes(160)),
	HARDWOOD_TREE("hardwood_tree", Duration.ofMinutes(640)),
	REDWOOD("redwood", Duration.ofMinutes(640)),
	SPIRIT_TREE("spirit_tree", Duration.ofMinutes(320)),
	CALQUAT("calquat", Duration.ofMinutes(160)),
	CELASTRUS("celastrus", Duration.ofMinutes(160)),
	CRYSTAL_TREE("crystal_tree", Duration.ofMinutes(80)),
	BUSH("bush", Duration.ofMinutes(20)),
	HOPS("hops", Duration.ofMinutes(10)),
	GRAPES("grapes", Duration.ofMinutes(5)),
	MUSHROOM("mushroom", Duration.ofMinutes(40)),
	HESPORI("hespori", Duration.ofMinutes(640)),
	BELLADONNA("belladonna", Duration.ofMinutes(80)),
	CACTUS("cactus", Duration.ofMinutes(80)),
	ANIMA("anima", Duration.ofMinutes(640)),
	CORAL("coral", Duration.ofMinutes(40)),
	SEAWEED("seaweed", Duration.ofMinutes(10));

	private final String wireName;
	private final Duration growthTick;

	FarmingPatchType(String wireName, Duration growthTick)
	{
		this.wireName = wireName;
		this.growthTick = growthTick;
	}

	String getWireName()
	{
		return wireName;
	}

	Duration getGrowthTick()
	{
		return growthTick;
	}
}

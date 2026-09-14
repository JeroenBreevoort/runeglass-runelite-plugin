package com.runeglass.runelite;

import java.time.Duration;

enum FarmingCrop
{
	POTATO("potato", FarmingPatchType.ALLOTMENT, 5),
	ONION("onion", FarmingPatchType.ALLOTMENT, 5),
	CABBAGE("cabbage", FarmingPatchType.ALLOTMENT, 5),
	TOMATO("tomato", FarmingPatchType.ALLOTMENT, 5),
	SWEETCORN("sweetcorn", FarmingPatchType.ALLOTMENT, 7),
	STRAWBERRY("strawberry", FarmingPatchType.ALLOTMENT, 7),
	WATERMELON("watermelon", FarmingPatchType.ALLOTMENT, 9),
	SNAPE_GRASS("snape_grass", FarmingPatchType.ALLOTMENT, 8),

	MARIGOLD("marigold", FarmingPatchType.FLOWER, 5),
	ROSEMARY("rosemary", FarmingPatchType.FLOWER, 5),
	NASTURTIUM("nasturtium", FarmingPatchType.FLOWER, 5),
	WOAD("woad", FarmingPatchType.FLOWER, 5),
	LIMPWURT("limpwurt", FarmingPatchType.FLOWER, 5),
	WHITE_LILY("white_lily", FarmingPatchType.FLOWER, 5),

	GUAM("guam", FarmingPatchType.HERB, 5, 4, 8, 128),
	MARRENTILL("marrentill", FarmingPatchType.HERB, 5, 11, 15, 131),
	TARROMIN("tarromin", FarmingPatchType.HERB, 5, 18, 22, 134),
	HARRALANDER("harralander", FarmingPatchType.HERB, 5, 25, 29, 137),
	RANARR("ranarr", FarmingPatchType.HERB, 5, 32, 36, 140),
	TOADFLAX("toadflax", FarmingPatchType.HERB, 5, 39, 43, 143),
	IRIT("irit", FarmingPatchType.HERB, 5, 46, 50, 146),
	AVANTOE("avantoe", FarmingPatchType.HERB, 5, 53, 57, 149),
	HUASCA("huasca", FarmingPatchType.HERB, 5, 60, 64, 173),
	KWUARM("kwuarm", FarmingPatchType.HERB, 5, 68, 72, 152),
	SNAPDRAGON("snapdragon", FarmingPatchType.HERB, 5, 75, 79, 155),
	CADANTINE("cadantine", FarmingPatchType.HERB, 5, 82, 86, 158),
	LANTADYME("lantadyme", FarmingPatchType.HERB, 5, 89, 93, 161),
	DWARF_WEED("dwarf_weed", FarmingPatchType.HERB, 5, 96, 100, 164),
	TORSTOL("torstol", FarmingPatchType.HERB, 5, 103, 107, 167),
	GOUTWEED("goutweed", FarmingPatchType.HERB, 5, 192, 196, 198),

	OAK("oak", FarmingPatchType.TREE, 5),
	WILLOW("willow", FarmingPatchType.TREE, 7),
	MAPLE("maple", FarmingPatchType.TREE, 9),
	YEW("yew", FarmingPatchType.TREE, 11),
	MAGIC("magic", FarmingPatchType.TREE, 13),

	APPLE("apple", FarmingPatchType.FRUIT_TREE, 7),
	BANANA("banana", FarmingPatchType.FRUIT_TREE, 7),
	ORANGE("orange", FarmingPatchType.FRUIT_TREE, 7),
	CURRY("curry", FarmingPatchType.FRUIT_TREE, 7),
	PINEAPPLE("pineapple", FarmingPatchType.FRUIT_TREE, 7),
	PAPAYA("papaya", FarmingPatchType.FRUIT_TREE, 7),
	PALM("palm", FarmingPatchType.FRUIT_TREE, 7),
	DRAGONFRUIT("dragonfruit", FarmingPatchType.FRUIT_TREE, 7),

	TEAK("teak", FarmingPatchType.HARDWOOD_TREE, 8),
	MAHOGANY("mahogany", FarmingPatchType.HARDWOOD_TREE, 9),
	CAMPHOR("camphor", FarmingPatchType.HARDWOOD_TREE, 9),
	IRONWOOD("ironwood", FarmingPatchType.HARDWOOD_TREE, 9),
	ROSEWOOD("rosewood", FarmingPatchType.HARDWOOD_TREE, 10),

	REDBERRY("redberry", FarmingPatchType.BUSH, 6),
	CADAVABERRY("cadavaberry", FarmingPatchType.BUSH, 7),
	DWELLBERRY("dwellberry", FarmingPatchType.BUSH, 8),
	JANGERBERRY("jangerberry", FarmingPatchType.BUSH, 9),
	WHITEBERRY("whiteberry", FarmingPatchType.BUSH, 9),
	POISON_IVY("poison_ivy", FarmingPatchType.BUSH, 9),

	BARLEY("barley", FarmingPatchType.HOPS, 5),
	HAMMERSTONE("hammerstone", FarmingPatchType.HOPS, 5),
	ASGARNIAN("asgarnian", FarmingPatchType.HOPS, 6),
	JUTE("jute", FarmingPatchType.HOPS, 6),
	YANILLIAN("yanillian", FarmingPatchType.HOPS, 7),
	FLAX("flax", FarmingPatchType.HOPS, 4, Duration.ofMinutes(20)),
	KRANDORIAN("krandorian", FarmingPatchType.HOPS, 8),
	WILDBLOOD("wildblood", FarmingPatchType.HOPS, 9),
	HEMP("hemp", FarmingPatchType.HOPS, 5, Duration.ofMinutes(20)),
	COTTON("cotton", FarmingPatchType.HOPS, 6, Duration.ofMinutes(20)),

	MUSHROOM("mushroom", FarmingPatchType.MUSHROOM, 7),
	BELLADONNA("belladonna", FarmingPatchType.BELLADONNA, 5),
	CACTUS("cactus", FarmingPatchType.CACTUS, 8),
	POTATO_CACTUS("potato_cactus", FarmingPatchType.CACTUS, 8, Duration.ofMinutes(10)),
	SEAWEED("seaweed", FarmingPatchType.SEAWEED, 5),
	GRAPE("grape", FarmingPatchType.GRAPES, 8),
	HESPORI("hespori", FarmingPatchType.HESPORI, 4),
	ATTAS("attas", FarmingPatchType.ANIMA, 9),
	IASOR("iasor", FarmingPatchType.ANIMA, 9),
	KRONOS("kronos", FarmingPatchType.ANIMA, 9),
	ELKHORN("elkhorn", FarmingPatchType.CORAL, 5),
	PILLAR("pillar", FarmingPatchType.CORAL, 5),
	UMBRAL("umbral", FarmingPatchType.CORAL, 5),
	SPIRIT_TREE("spirit_tree", FarmingPatchType.SPIRIT_TREE, 13),
	REDWOOD("redwood", FarmingPatchType.REDWOOD, 11),
	CALQUAT("calquat", FarmingPatchType.CALQUAT, 9),
	CELASTRUS("celastrus", FarmingPatchType.CELASTRUS, 6),
	CRYSTAL_TREE("crystal_tree", FarmingPatchType.CRYSTAL_TREE, 7);

	private final String wireName;
	private final FarmingPatchType patchType;
	private final int stages;
	private final Integer growingStart;
	private final Integer harvestableStart;
	private final Integer diseasedStart;
	private final Duration growthTick;

	FarmingCrop(String wireName, FarmingPatchType patchType, int stages)
	{
		this(wireName, patchType, stages, null, null, null, patchType.getGrowthTick());
	}

	FarmingCrop(String wireName, FarmingPatchType patchType, int stages, Duration growthTick)
	{
		this(wireName, patchType, stages, null, null, null, growthTick);
	}

	FarmingCrop(
		String wireName,
		FarmingPatchType patchType,
		int stages,
		Integer growingStart,
		Integer harvestableStart,
		Integer diseasedStart)
	{
		this(
			wireName,
			patchType,
			stages,
			growingStart,
			harvestableStart,
			diseasedStart,
			patchType.getGrowthTick());
	}

	FarmingCrop(
		String wireName,
		FarmingPatchType patchType,
		int stages,
		Integer growingStart,
		Integer harvestableStart,
		Integer diseasedStart,
		Duration growthTick)
	{
		this.wireName = wireName;
		this.patchType = patchType;
		this.stages = stages;
		this.growingStart = growingStart;
		this.harvestableStart = harvestableStart;
		this.diseasedStart = diseasedStart;
		this.growthTick = growthTick;
	}

	String getWireName()
	{
		return wireName;
	}

	int getStages()
	{
		return stages;
	}

	FarmingPatchType getPatchType()
	{
		return patchType;
	}

	Duration getGrowthTick()
	{
		return growthTick;
	}

	boolean isGrowing(int value)
	{
		return growingStart != null
			&& value >= growingStart
			&& value < harvestableStart;
	}

	boolean isHarvestable(int value)
	{
		int length = this == GOUTWEED ? 2 : 3;
		return harvestableStart != null
			&& value >= harvestableStart
			&& value < harvestableStart + length;
	}

	boolean isDiseased(int value)
	{
		return diseasedStart != null
			&& value >= diseasedStart
			&& value < diseasedStart + 3;
	}

	int growingStage(int value)
	{
		if (growingStart == null)
		{
			throw new IllegalStateException("Crop has no herb growing range");
		}
		return value - growingStart;
	}

	int harvestableStage(int value)
	{
		if (harvestableStart == null)
		{
			throw new IllegalStateException("Crop has no herb harvestable range");
		}
		return Math.max(0, stages - 1 - (value - harvestableStart));
	}

	int diseasedStage(int value)
	{
		if (diseasedStart == null)
		{
			throw new IllegalStateException("Crop has no herb disease range");
		}
		return Math.min(stages - 1, value - diseasedStart + 1);
	}
}

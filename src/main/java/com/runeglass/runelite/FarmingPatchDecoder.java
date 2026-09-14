package com.runeglass.runelite;

import java.util.Objects;

final class FarmingPatchDecoder
{
	private static final Range[] ALLOTMENT_RANGES = {
		r(6, 9, FarmingCrop.POTATO, FarmingPatchState.GROWING, 0),
		r(10, 12, FarmingCrop.POTATO, FarmingPatchState.HARVESTABLE, 0),
		r(13, 16, FarmingCrop.ONION, FarmingPatchState.GROWING, 0),
		r(17, 19, FarmingCrop.ONION, FarmingPatchState.HARVESTABLE, 0),
		r(20, 23, FarmingCrop.CABBAGE, FarmingPatchState.GROWING, 0),
		r(24, 26, FarmingCrop.CABBAGE, FarmingPatchState.HARVESTABLE, 0),
		r(27, 30, FarmingCrop.TOMATO, FarmingPatchState.GROWING, 0),
		r(31, 33, FarmingCrop.TOMATO, FarmingPatchState.HARVESTABLE, 0),
		r(34, 39, FarmingCrop.SWEETCORN, FarmingPatchState.GROWING, 0),
		r(40, 42, FarmingCrop.SWEETCORN, FarmingPatchState.HARVESTABLE, 0),
		r(43, 48, FarmingCrop.STRAWBERRY, FarmingPatchState.GROWING, 0),
		r(49, 51, FarmingCrop.STRAWBERRY, FarmingPatchState.HARVESTABLE, 0),
		r(52, 59, FarmingCrop.WATERMELON, FarmingPatchState.GROWING, 0),
		r(60, 62, FarmingCrop.WATERMELON, FarmingPatchState.HARVESTABLE, 0),
		r(63, 69, FarmingCrop.SNAPE_GRASS, FarmingPatchState.GROWING, 0),
		r(70, 73, FarmingCrop.POTATO, FarmingPatchState.GROWING, 0),
		r(77, 80, FarmingCrop.ONION, FarmingPatchState.GROWING, 0),
		r(84, 87, FarmingCrop.CABBAGE, FarmingPatchState.GROWING, 0),
		r(91, 94, FarmingCrop.TOMATO, FarmingPatchState.GROWING, 0),
		r(98, 103, FarmingCrop.SWEETCORN, FarmingPatchState.GROWING, 0),
		r(107, 112, FarmingCrop.STRAWBERRY, FarmingPatchState.GROWING, 0),
		r(116, 123, FarmingCrop.WATERMELON, FarmingPatchState.GROWING, 0),
		r(128, 134, FarmingCrop.SNAPE_GRASS, FarmingPatchState.GROWING, 0),
		r(135, 137, FarmingCrop.POTATO, FarmingPatchState.DISEASED, 1),
		r(138, 140, FarmingCrop.SNAPE_GRASS, FarmingPatchState.HARVESTABLE, 0),
		r(142, 144, FarmingCrop.ONION, FarmingPatchState.DISEASED, 1),
		r(149, 151, FarmingCrop.CABBAGE, FarmingPatchState.DISEASED, 1),
		r(156, 158, FarmingCrop.TOMATO, FarmingPatchState.DISEASED, 1),
		r(163, 167, FarmingCrop.SWEETCORN, FarmingPatchState.DISEASED, 1),
		r(172, 176, FarmingCrop.STRAWBERRY, FarmingPatchState.DISEASED, 1),
		r(181, 187, FarmingCrop.WATERMELON, FarmingPatchState.DISEASED, 1),
		r(193, 195, FarmingCrop.SNAPE_GRASS, FarmingPatchState.DEAD, 1),
		r(196, 198, FarmingCrop.SNAPE_GRASS, FarmingPatchState.DISEASED, 1),
		r(199, 201, FarmingCrop.POTATO, FarmingPatchState.DEAD, 1),
		r(202, 204, FarmingCrop.SNAPE_GRASS, FarmingPatchState.DISEASED, 4),
		r(206, 208, FarmingCrop.ONION, FarmingPatchState.DEAD, 1),
		r(209, 211, FarmingCrop.SNAPE_GRASS, FarmingPatchState.DEAD, 4),
		r(213, 215, FarmingCrop.CABBAGE, FarmingPatchState.DEAD, 1),
		r(220, 222, FarmingCrop.TOMATO, FarmingPatchState.DEAD, 1),
		r(227, 231, FarmingCrop.SWEETCORN, FarmingPatchState.DEAD, 1),
		r(236, 240, FarmingCrop.STRAWBERRY, FarmingPatchState.DEAD, 1),
		r(245, 251, FarmingCrop.WATERMELON, FarmingPatchState.DEAD, 1),
	};

	private static final Range[] FLOWER_RANGES = {
		r(8, 11, FarmingCrop.MARIGOLD, FarmingPatchState.GROWING, 0),
		r(12, 12, FarmingCrop.MARIGOLD, FarmingPatchState.HARVESTABLE, 0),
		r(13, 16, FarmingCrop.ROSEMARY, FarmingPatchState.GROWING, 0),
		r(17, 17, FarmingCrop.ROSEMARY, FarmingPatchState.HARVESTABLE, 0),
		r(18, 21, FarmingCrop.NASTURTIUM, FarmingPatchState.GROWING, 0),
		r(22, 22, FarmingCrop.NASTURTIUM, FarmingPatchState.HARVESTABLE, 0),
		r(23, 26, FarmingCrop.WOAD, FarmingPatchState.GROWING, 0),
		r(27, 27, FarmingCrop.WOAD, FarmingPatchState.HARVESTABLE, 0),
		r(28, 31, FarmingCrop.LIMPWURT, FarmingPatchState.GROWING, 0),
		r(32, 32, FarmingCrop.LIMPWURT, FarmingPatchState.HARVESTABLE, 0),
		r(37, 40, FarmingCrop.WHITE_LILY, FarmingPatchState.GROWING, 0),
		r(41, 41, FarmingCrop.WHITE_LILY, FarmingPatchState.HARVESTABLE, 0),
		r(72, 75, FarmingCrop.MARIGOLD, FarmingPatchState.GROWING, 0),
		r(77, 80, FarmingCrop.ROSEMARY, FarmingPatchState.GROWING, 0),
		r(82, 85, FarmingCrop.NASTURTIUM, FarmingPatchState.GROWING, 0),
		r(87, 90, FarmingCrop.WOAD, FarmingPatchState.GROWING, 0),
		r(92, 95, FarmingCrop.LIMPWURT, FarmingPatchState.GROWING, 0),
		r(101, 104, FarmingCrop.WHITE_LILY, FarmingPatchState.GROWING, 0),
		r(137, 139, FarmingCrop.MARIGOLD, FarmingPatchState.DISEASED, 1),
		r(142, 144, FarmingCrop.ROSEMARY, FarmingPatchState.DISEASED, 1),
		r(147, 149, FarmingCrop.NASTURTIUM, FarmingPatchState.DISEASED, 1),
		r(152, 154, FarmingCrop.WOAD, FarmingPatchState.DISEASED, 1),
		r(157, 159, FarmingCrop.LIMPWURT, FarmingPatchState.DISEASED, 1),
		r(166, 168, FarmingCrop.WHITE_LILY, FarmingPatchState.DISEASED, 1),
		r(201, 204, FarmingCrop.MARIGOLD, FarmingPatchState.DEAD, 1),
		r(206, 209, FarmingCrop.ROSEMARY, FarmingPatchState.DEAD, 1),
		r(211, 214, FarmingCrop.NASTURTIUM, FarmingPatchState.DEAD, 1),
		r(216, 219, FarmingCrop.WOAD, FarmingPatchState.DEAD, 1),
		r(221, 224, FarmingCrop.LIMPWURT, FarmingPatchState.DEAD, 1),
		r(230, 233, FarmingCrop.WHITE_LILY, FarmingPatchState.DEAD, 1),
	};

	private static final Range[] BUSH_RANGES = {
		r(5, 9, FarmingCrop.REDBERRY, FarmingPatchState.GROWING, 0),
		r(10, 14, FarmingCrop.REDBERRY, FarmingPatchState.HARVESTABLE, 0),
		r(15, 20, FarmingCrop.CADAVABERRY, FarmingPatchState.GROWING, 0),
		r(21, 25, FarmingCrop.CADAVABERRY, FarmingPatchState.HARVESTABLE, 0),
		r(26, 32, FarmingCrop.DWELLBERRY, FarmingPatchState.GROWING, 0),
		r(33, 37, FarmingCrop.DWELLBERRY, FarmingPatchState.HARVESTABLE, 0),
		r(38, 45, FarmingCrop.JANGERBERRY, FarmingPatchState.GROWING, 0),
		r(46, 50, FarmingCrop.JANGERBERRY, FarmingPatchState.HARVESTABLE, 0),
		r(51, 58, FarmingCrop.WHITEBERRY, FarmingPatchState.GROWING, 0),
		r(59, 63, FarmingCrop.WHITEBERRY, FarmingPatchState.HARVESTABLE, 0),
		r(70, 74, FarmingCrop.REDBERRY, FarmingPatchState.DISEASED, 1),
		r(80, 85, FarmingCrop.CADAVABERRY, FarmingPatchState.DISEASED, 1),
		r(91, 97, FarmingCrop.DWELLBERRY, FarmingPatchState.DISEASED, 1),
		r(103, 110, FarmingCrop.JANGERBERRY, FarmingPatchState.DISEASED, 1),
		r(116, 123, FarmingCrop.WHITEBERRY, FarmingPatchState.DISEASED, 1),
		r(134, 138, FarmingCrop.REDBERRY, FarmingPatchState.DEAD, 1),
		r(144, 149, FarmingCrop.CADAVABERRY, FarmingPatchState.DEAD, 1),
		r(155, 161, FarmingCrop.DWELLBERRY, FarmingPatchState.DEAD, 1),
		r(167, 174, FarmingCrop.JANGERBERRY, FarmingPatchState.DEAD, 1),
		r(180, 187, FarmingCrop.WHITEBERRY, FarmingPatchState.DEAD, 1),
		r(197, 204, FarmingCrop.POISON_IVY, FarmingPatchState.GROWING, 0),
		r(205, 209, FarmingCrop.POISON_IVY, FarmingPatchState.HARVESTABLE, 0),
		r(210, 216, FarmingCrop.POISON_IVY, FarmingPatchState.DISEASED, 1),
		r(217, 224, FarmingCrop.POISON_IVY, FarmingPatchState.DEAD, 1),
		r(225, 225, FarmingCrop.POISON_IVY, FarmingPatchState.DISEASED, 8),
		r(250, 250, FarmingCrop.REDBERRY, FarmingPatchState.GROWING, 5),
		r(251, 251, FarmingCrop.CADAVABERRY, FarmingPatchState.GROWING, 6),
		r(252, 252, FarmingCrop.DWELLBERRY, FarmingPatchState.GROWING, 7),
		r(253, 253, FarmingCrop.JANGERBERRY, FarmingPatchState.GROWING, 8),
		r(254, 254, FarmingCrop.WHITEBERRY, FarmingPatchState.GROWING, 8),
		r(255, 255, FarmingCrop.POISON_IVY, FarmingPatchState.GROWING, 8),
	};

	private static final Range[] HOPS_RANGES = combine(
		cropCycle(FarmingCrop.HAMMERSTONE, 4, 8, 11, 132, 139),
		cropCycle(FarmingCrop.ASGARNIAN, 14, 19, 22, 142, 150),
		cropCycle(FarmingCrop.YANILLIAN, 26, 32, 35, 154, 163),
		cropCycle(FarmingCrop.KRANDORIAN, 40, 47, 50, 168, 178),
		cropCycle(FarmingCrop.WILDBLOOD, 56, 64, 67, 184, 195),
		cropCycle(FarmingCrop.BARLEY, 74, 78, 81, 202, 209),
		cropCycle(FarmingCrop.JUTE, 84, 89, 92, 212, 220),
		cropCycle(FarmingCrop.FLAX, 96, 99, 102, 224, 230),
		cropCycle(FarmingCrop.HEMP, 104, 108, 111, 232, 239),
		cropCycle(FarmingCrop.COTTON, 114, 119, 122, 242, 250));
	private static final Range[] CALQUAT_RANGES = {
		r(4, 11, FarmingCrop.CALQUAT, FarmingPatchState.GROWING, 0),
		r(12, 18, FarmingCrop.CALQUAT, FarmingPatchState.HARVESTABLE, 0),
		r(19, 25, FarmingCrop.CALQUAT, FarmingPatchState.DISEASED, 1),
		r(26, 33, FarmingCrop.CALQUAT, FarmingPatchState.DEAD, 1),
		r(34, 34, FarmingCrop.CALQUAT, FarmingPatchState.GROWING, 8),
	};
	private static final Range[] CELASTRUS_RANGES = {
		r(8, 12, FarmingCrop.CELASTRUS, FarmingPatchState.GROWING, 0),
		r(13, 13, FarmingCrop.CELASTRUS, FarmingPatchState.GROWING, 5),
		r(14, 16, FarmingCrop.CELASTRUS, FarmingPatchState.HARVESTABLE, 0),
		r(17, 17, FarmingCrop.CELASTRUS, FarmingPatchState.HARVESTABLE, 0),
		r(18, 22, FarmingCrop.CELASTRUS, FarmingPatchState.DISEASED, 1),
		r(23, 27, FarmingCrop.CELASTRUS, FarmingPatchState.DEAD, 1),
		r(28, 28, FarmingCrop.CELASTRUS, FarmingPatchState.HARVESTABLE, 0),
	};
	private static final Range[] MUSHROOM_RANGES = {
		r(4, 9, FarmingCrop.MUSHROOM, FarmingPatchState.GROWING, 0),
		r(10, 15, FarmingCrop.MUSHROOM, FarmingPatchState.HARVESTABLE, 0),
		r(16, 20, FarmingCrop.MUSHROOM, FarmingPatchState.DISEASED, 1),
		r(21, 25, FarmingCrop.MUSHROOM, FarmingPatchState.DEAD, 1),
	};
	private static final Range[] BELLADONNA_RANGES = {
		r(4, 7, FarmingCrop.BELLADONNA, FarmingPatchState.GROWING, 0),
		r(8, 8, FarmingCrop.BELLADONNA, FarmingPatchState.HARVESTABLE, 0),
		r(9, 11, FarmingCrop.BELLADONNA, FarmingPatchState.DISEASED, 1),
		r(12, 14, FarmingCrop.BELLADONNA, FarmingPatchState.DEAD, 1),
	};
	private static final Range[] CACTUS_RANGES = {
		r(8, 14, FarmingCrop.CACTUS, FarmingPatchState.GROWING, 0),
		r(15, 18, FarmingCrop.CACTUS, FarmingPatchState.HARVESTABLE, 0),
		r(19, 24, FarmingCrop.CACTUS, FarmingPatchState.DISEASED, 1),
		r(25, 30, FarmingCrop.CACTUS, FarmingPatchState.DEAD, 1),
		r(31, 31, FarmingCrop.CACTUS, FarmingPatchState.GROWING, 7),
		r(32, 38, FarmingCrop.POTATO_CACTUS, FarmingPatchState.GROWING, 0),
		r(39, 45, FarmingCrop.POTATO_CACTUS, FarmingPatchState.HARVESTABLE, 0),
		r(46, 51, FarmingCrop.POTATO_CACTUS, FarmingPatchState.DISEASED, 1),
		r(52, 57, FarmingCrop.POTATO_CACTUS, FarmingPatchState.DEAD, 1),
		r(58, 58, FarmingCrop.POTATO_CACTUS, FarmingPatchState.GROWING, 7),
	};
	private static final Range[] ANIMA_RANGES = {
		r(8, 16, FarmingCrop.ATTAS, FarmingPatchState.GROWING, 0),
		r(17, 25, FarmingCrop.IASOR, FarmingPatchState.GROWING, 0),
		r(26, 34, FarmingCrop.KRONOS, FarmingPatchState.GROWING, 0),
	};
	private static final Range[] CORAL_RANGES = {
		r(4, 8, FarmingCrop.ELKHORN, FarmingPatchState.GROWING, 0),
		r(9, 11, FarmingCrop.ELKHORN, FarmingPatchState.DISEASED, 1),
		r(12, 14, FarmingCrop.ELKHORN, FarmingPatchState.DEAD, 1),
		r(15, 19, FarmingCrop.PILLAR, FarmingPatchState.GROWING, 0),
		r(20, 22, FarmingCrop.PILLAR, FarmingPatchState.DISEASED, 1),
		r(23, 25, FarmingCrop.PILLAR, FarmingPatchState.DEAD, 1),
		r(26, 30, FarmingCrop.UMBRAL, FarmingPatchState.GROWING, 0),
		r(31, 33, FarmingCrop.UMBRAL, FarmingPatchState.DISEASED, 1),
		r(34, 36, FarmingCrop.UMBRAL, FarmingPatchState.DEAD, 1),
	};
	private static final Range[] SEAWEED_RANGES = {
		r(4, 7, FarmingCrop.SEAWEED, FarmingPatchState.GROWING, 0),
		r(8, 10, FarmingCrop.SEAWEED, FarmingPatchState.HARVESTABLE, 0),
		r(11, 13, FarmingCrop.SEAWEED, FarmingPatchState.DISEASED, 1),
		r(14, 16, FarmingCrop.SEAWEED, FarmingPatchState.DEAD, 1),
	};

	private FarmingPatchDecoder()
	{
	}

	static DecodedState decode(FarmingPatchType patchType, int value)
	{
		switch (patchType)
		{
			case ALLOTMENT:
				return decodeWithEmptyFallback(value, ALLOTMENT_RANGES);
			case FLOWER:
				return decodeWithEmptyFallback(value, FLOWER_RANGES);
			case HERB:
				return decodeHerb(value);
			case TREE:
				return decodeTree(value);
			case FRUIT_TREE:
				return decodeFruitTree(value);
			case HARDWOOD_TREE:
				return decodeHardwoodTree(value);
			case REDWOOD:
				return decodeRedwood(value);
			case SPIRIT_TREE:
				return decodeSpiritTree(value);
			case CALQUAT:
				return decodeCalquat(value);
			case CELASTRUS:
				return decodeCelastrus(value);
			case CRYSTAL_TREE:
				return decodeCrystalTree(value);
			case BUSH:
				return decodeWithEmptyFallback(value, BUSH_RANGES);
			case HOPS:
				return decodeWithEmptyFallback(value, HOPS_RANGES);
			case GRAPES:
				return decodeGrapes(value);
			case MUSHROOM:
				return decodeMushroom(value);
			case HESPORI:
				return decodeHespori(value);
			case BELLADONNA:
				return decodeBelladonna(value);
			case CACTUS:
				return decodeCactus(value);
			case ANIMA:
				return decodeAnima(value);
			case CORAL:
				return decodeCoral(value);
			case SEAWEED:
				return decodeSeaweed(value);
			default:
				return null;
		}
	}

	private static DecodedState decodeHerb(int value)
	{
		if (isHerbEmpty(value))
		{
			return empty();
		}
		if (value >= 170 && value <= 172)
		{
			return terminal(FarmingPatchState.DEAD, null, null);
		}
		if (value >= 201 && value <= 203)
		{
			return terminal(FarmingPatchState.DEAD, FarmingCrop.GOUTWEED, null);
		}
		for (FarmingCrop crop : FarmingCrop.values())
		{
			if (crop.getPatchType() != FarmingPatchType.HERB)
			{
				continue;
			}
			if (crop.isGrowing(value))
			{
				return growing(crop, crop.growingStage(value));
			}
			if (crop.isHarvestable(value))
			{
				return terminal(FarmingPatchState.HARVESTABLE, crop, crop.harvestableStage(value));
			}
			if (crop.isDiseased(value))
			{
				return terminal(FarmingPatchState.DISEASED, crop, crop.diseasedStage(value));
			}
		}
		return null;
	}

	private static DecodedState decodeTree(int value)
	{
		if (isTreeEmpty(value))
		{
			return empty();
		}
		DecodedState decoded = decodeTreeCrop(value, FarmingCrop.OAK, 8, 12, 73, 75, 77, 137, 139, 141);
		if (decoded == null)
		{
			decoded = decodeTreeCrop(value, FarmingCrop.WILLOW, 15, 21, 80, 84, 86, 144, 148, 150);
		}
		if (decoded == null)
		{
			decoded = decodeTreeCrop(value, FarmingCrop.MAPLE, 24, 32, 89, 95, 97, 153, 159, 161);
		}
		if (decoded == null)
		{
			decoded = decodeTreeCrop(value, FarmingCrop.YEW, 35, 45, 100, 108, 110, 164, 172, 174);
		}
		if (decoded == null)
		{
			decoded = decodeTreeCrop(value, FarmingCrop.MAGIC, 48, 60, 113, 123, 125, 177, 187, 189);
		}
		if (decoded == null && value >= 192 && value <= 197)
		{
			return terminal(FarmingPatchState.HARVESTABLE, FarmingCrop.WILLOW, 0);
		}
		return decoded;
	}

	private static DecodedState decodeTreeCrop(
		int value,
		FarmingCrop crop,
		int growingStart,
		int healthValue,
		int diseasedStart,
		int diseasedEnd,
		int diseasedFinal,
		int deadStart,
		int deadEnd,
		int deadFinal)
	{
		if (value >= growingStart && value < healthValue)
		{
			return growing(crop, value - growingStart);
		}
		if (value == healthValue)
		{
			return growing(crop, crop.getStages() - 1);
		}
		if (value == healthValue + 1 || value == healthValue + 2)
		{
			return terminal(FarmingPatchState.HARVESTABLE, crop, 0);
		}
		if (value >= diseasedStart && value <= diseasedEnd)
		{
			return terminal(FarmingPatchState.DISEASED, crop, value - diseasedStart + 1);
		}
		if (value == diseasedFinal)
		{
			return terminal(FarmingPatchState.DISEASED, crop, crop.getStages() - 1);
		}
		if (value >= deadStart && value <= deadEnd)
		{
			return terminal(FarmingPatchState.DEAD, crop, value - deadStart + 1);
		}
		if (value == deadFinal)
		{
			return terminal(FarmingPatchState.DEAD, crop, crop.getStages() - 1);
		}
		return null;
	}

	private static DecodedState decodeFruitTree(int value)
	{
		if (isFruitTreeEmpty(value))
		{
			return empty();
		}
		FarmingCrop[] crops = {
			FarmingCrop.APPLE,
			FarmingCrop.BANANA,
			FarmingCrop.ORANGE,
			FarmingCrop.CURRY,
			FarmingCrop.PINEAPPLE,
			FarmingCrop.PAPAYA,
			FarmingCrop.PALM,
			FarmingCrop.DRAGONFRUIT
		};
		int[] starts = {8, 35, 72, 99, 136, 163, 200, 227};
		for (int index = 0; index < crops.length; index++)
		{
			int start = starts[index];
			FarmingCrop crop = crops[index];
			if (value >= start && value <= start + 5)
			{
				return growing(crop, value - start);
			}
			if (value >= start + 6 && value <= start + 12)
			{
				return terminal(FarmingPatchState.HARVESTABLE, crop, value - start - 6);
			}
			if (value >= start + 13 && value <= start + 18)
			{
				return terminal(FarmingPatchState.DISEASED, crop, value - start - 12);
			}
			if (value >= start + 19 && value <= start + 24)
			{
				return terminal(FarmingPatchState.DEAD, crop, value - start - 18);
			}
			if (value == start + 25)
			{
				return terminal(FarmingPatchState.HARVESTABLE, crop, 0);
			}
			if (value == start + 26)
			{
				return growing(crop, crop.getStages() - 1);
			}
		}
		return null;
	}

	private static DecodedState decodeHardwoodTree(int value)
	{
		if ((value >= 0 && value <= 7) || (value >= 133 && value <= 255))
		{
			return empty();
		}
		FarmingCrop[] crops = {
			FarmingCrop.TEAK,
			FarmingCrop.MAHOGANY,
			FarmingCrop.CAMPHOR,
			FarmingCrop.IRONWOOD,
			FarmingCrop.ROSEWOOD
		};
		int[] starts = {8, 30, 55, 80, 105};
		for (int index = 0; index < crops.length; index++)
		{
			FarmingCrop crop = crops[index];
			int start = starts[index];
			int stages = crop.getStages();
			if (value >= start && value < start + stages)
			{
				return growing(crop, value - start);
			}
			if (value == start + stages || value == start + stages + 1)
			{
				return terminal(FarmingPatchState.HARVESTABLE, crop, 0);
			}
			int diseasedStart = start + stages + 2;
			if (value >= diseasedStart && value <= start + 2 * stages - 1)
			{
				return terminal(FarmingPatchState.DISEASED, crop, value - diseasedStart + 1);
			}
			int deadStart = start + 2 * stages;
			if (value >= deadStart && value <= start + 3 * stages - 3)
			{
				return terminal(FarmingPatchState.DEAD, crop, value - deadStart + 1);
			}
		}
		return null;
	}

	private static DecodedState decodeRedwood(int value)
	{
		if (value >= 0 && value <= 7)
		{
			return empty();
		}
		if (value >= 8 && value <= 17)
		{
			return growing(FarmingCrop.REDWOOD, value - 8);
		}
		if (value == 18 || (value >= 41 && value <= 55))
		{
			return terminal(FarmingPatchState.HARVESTABLE, FarmingCrop.REDWOOD, 0);
		}
		if (value >= 19 && value <= 27)
		{
			return terminal(FarmingPatchState.DISEASED, FarmingCrop.REDWOOD, value - 18);
		}
		if (value >= 28 && value <= 36)
		{
			return terminal(FarmingPatchState.DEAD, FarmingCrop.REDWOOD, value - 27);
		}
		return value == 37 ? growing(FarmingCrop.REDWOOD, 10) : null;
	}

	private static DecodedState decodeSpiritTree(int value)
	{
		if ((value >= 0 && value <= 7) || (value >= 45 && value <= 63))
		{
			return empty();
		}
		if (value >= 8 && value <= 20)
		{
			return growing(FarmingCrop.SPIRIT_TREE, value - 8);
		}
		if (value >= 21 && value <= 31)
		{
			return terminal(FarmingPatchState.DISEASED, FarmingCrop.SPIRIT_TREE, value - 20);
		}
		if (value >= 32 && value <= 43)
		{
			return terminal(FarmingPatchState.DEAD, FarmingCrop.SPIRIT_TREE, value - 31);
		}
		return value == 44 ? growing(FarmingCrop.SPIRIT_TREE, 12) : null;
	}

	private static DecodedState decodeCalquat(int value)
	{
		return decodeWithEmptyFallback(value, CALQUAT_RANGES);
	}

	private static DecodedState decodeCelastrus(int value)
	{
		return decodeWithEmptyFallback(value, CELASTRUS_RANGES);
	}

	private static DecodedState decodeCrystalTree(int value)
	{
		if (value >= 0 && value <= 3)
		{
			return empty();
		}
		if (value >= 8 && value <= 14)
		{
			return growing(FarmingCrop.CRYSTAL_TREE, value - 8);
		}
		return value == 15
			? terminal(FarmingPatchState.HARVESTABLE, FarmingCrop.CRYSTAL_TREE, 0)
			: null;
	}

	private static DecodedState decodeGrapes(int value)
	{
		if (value >= 0 && value <= 1)
		{
			return empty();
		}
		if (value >= 2 && value <= 10)
		{
			return growing(FarmingCrop.GRAPE, Math.min(7, value - 2));
		}
		return value >= 11 && value <= 15
			? terminal(FarmingPatchState.HARVESTABLE, FarmingCrop.GRAPE, value - 11)
			: null;
	}

	private static DecodedState decodeMushroom(int value)
	{
		return decodeWithEmptyFallback(value, MUSHROOM_RANGES);
	}

	private static DecodedState decodeHespori(int value)
	{
		if ((value >= 0 && value <= 3) || value == 9)
		{
			return empty();
		}
		if (value >= 4 && value <= 6)
		{
			return growing(FarmingCrop.HESPORI, value - 4);
		}
		return value >= 7 && value <= 8
			? terminal(FarmingPatchState.HARVESTABLE, FarmingCrop.HESPORI, value - 7)
			: null;
	}

	private static DecodedState decodeBelladonna(int value)
	{
		return decodeWithEmptyFallback(value, BELLADONNA_RANGES);
	}

	private static DecodedState decodeCactus(int value)
	{
		return decodeWithEmptyFallback(value, CACTUS_RANGES);
	}

	private static DecodedState decodeAnima(int value)
	{
		return decodeWithEmptyFallback(value, ANIMA_RANGES);
	}

	private static DecodedState decodeCoral(int value)
	{
		return decodeWithEmptyFallback(value, CORAL_RANGES);
	}

	private static DecodedState decodeSeaweed(int value)
	{
		return decodeWithEmptyFallback(value, SEAWEED_RANGES);
	}

	private static Range[] cropCycle(
		FarmingCrop crop,
		int growingStart,
		int harvestStart,
		int diseasedStart,
		int wateredStart,
		int deadStart)
	{
		return new Range[] {
			r(growingStart, growingStart + crop.getStages() - 2, crop, FarmingPatchState.GROWING, 0),
			rr(harvestStart, harvestStart + 2, crop, FarmingPatchState.HARVESTABLE, 2),
			r(diseasedStart, diseasedStart + crop.getStages() - 3, crop, FarmingPatchState.DISEASED, 1),
			r(wateredStart, wateredStart + crop.getStages() - 2, crop, FarmingPatchState.GROWING, 0),
			r(deadStart, deadStart + crop.getStages() - 3, crop, FarmingPatchState.DEAD, 1),
		};
	}

	private static Range[] combine(Range[]... groups)
	{
		int length = 0;
		for (Range[] group : groups)
		{
			length += group.length;
		}
		Range[] combined = new Range[length];
		int offset = 0;
		for (Range[] group : groups)
		{
			System.arraycopy(group, 0, combined, offset, group.length);
			offset += group.length;
		}
		return combined;
	}

	private static DecodedState decodeWithEmptyFallback(int value, Range[] ranges)
	{
		DecodedState decoded = decodeRanges(value, ranges);
		return decoded == null && value >= 0 && value <= 255 ? empty() : decoded;
	}

	private static DecodedState decodeRanges(int value, Range[] ranges)
	{
		for (Range range : ranges)
		{
			if (value >= range.start && value <= range.end)
			{
				int stage = range.stageAtStart + (value - range.start) * range.step;
				return range.state == FarmingPatchState.GROWING
					? growing(range.crop, stage)
					: terminal(range.state, range.crop, stage);
			}
		}
		return null;
	}

	private static Range r(
		int start,
		int end,
		FarmingCrop crop,
		FarmingPatchState state,
		int stageAtStart)
	{
		return new Range(start, end, crop, state, stageAtStart, 1);
	}

	private static Range rr(
		int start,
		int end,
		FarmingCrop crop,
		FarmingPatchState state,
		int stageAtStart)
	{
		return new Range(start, end, crop, state, stageAtStart, -1);
	}

	private static boolean isHerbEmpty(int value)
	{
		return (value >= 0 && value <= 3)
			|| value == 67
			|| (value >= 176 && value <= 191)
			|| (value >= 204 && value <= 219)
			|| (value >= 221 && value <= 255);
	}

	private static boolean isTreeEmpty(int value)
	{
		return (value >= 0 && value <= 7)
			|| (value >= 63 && value <= 72)
			|| (value >= 78 && value <= 79)
			|| (value >= 87 && value <= 88)
			|| (value >= 98 && value <= 99)
			|| (value >= 111 && value <= 112)
			|| (value >= 126 && value <= 136)
			|| (value >= 142 && value <= 143)
			|| (value >= 151 && value <= 152)
			|| (value >= 162 && value <= 163)
			|| (value >= 175 && value <= 176)
			|| (value >= 190 && value <= 191)
			|| (value >= 198 && value <= 255);
	}

	private static boolean isFruitTreeEmpty(int value)
	{
		return (value >= 0 && value <= 7)
			|| (value >= 62 && value <= 71)
			|| (value >= 126 && value <= 135)
			|| (value >= 190 && value <= 199)
			|| (value >= 254 && value <= 255);
	}

	private static DecodedState empty()
	{
		return new DecodedState(FarmingPatchState.EMPTY, null, null, null);
	}

	private static DecodedState growing(FarmingCrop crop, int stage)
	{
		return new DecodedState(FarmingPatchState.GROWING, crop, stage, crop.getStages());
	}

	private static DecodedState terminal(FarmingPatchState state, FarmingCrop crop, Integer stage)
	{
		return new DecodedState(
			state,
			crop,
			stage,
			crop == null || stage == null ? null : crop.getStages());
	}

	private static final class Range
	{
		private final int start;
		private final int end;
		private final FarmingCrop crop;
		private final FarmingPatchState state;
		private final int stageAtStart;
		private final int step;

		private Range(
			int start,
			int end,
			FarmingCrop crop,
			FarmingPatchState state,
			int stageAtStart,
			int step)
		{
			this.start = start;
			this.end = end;
			this.crop = crop;
			this.state = state;
			this.stageAtStart = stageAtStart;
			this.step = step;
		}
	}

	static final class DecodedState
	{
		final FarmingPatchState state;
		final FarmingCrop crop;
		final Integer stage;
		final Integer stages;

		private DecodedState(
			FarmingPatchState state,
			FarmingCrop crop,
			Integer stage,
			Integer stages)
		{
			this.state = state;
			this.crop = crop;
			this.stage = stage;
			this.stages = stages;
		}

		boolean sameSemanticValue(DecodedState other)
		{
			return other != null
				&& state == other.state
				&& crop == other.crop
				&& Objects.equals(stage, other.stage)
				&& Objects.equals(stages, other.stages);
		}
	}
}

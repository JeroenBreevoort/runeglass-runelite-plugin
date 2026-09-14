package com.runeglass.runelite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

enum FarmingPatchLocation
{
	ARDOUGNE_ALLOTMENT_N("ardougne_allotment_n", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 10548),
	ARDOUGNE_ALLOTMENT_S("ardougne_allotment_s", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 10548),
	CATHERBY_ALLOTMENT_N("catherby_allotment_n", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 11062, 11061, 11318, 11317)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInCatherbyAllotmentBounds(point);
		}
	},
	CATHERBY_ALLOTMENT_S("catherby_allotment_s", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 11062, 11061, 11318, 11317)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInCatherbyAllotmentBounds(point);
		}
	},
	CIVITAS_ALLOTMENT_N("civitas_allotment_n", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 6192, 6447, 6448, 6449, 6191, 6193),
	CIVITAS_ALLOTMENT_S("civitas_allotment_s", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 6192, 6447, 6448, 6449, 6191, 6193),
	FALADOR_ALLOTMENT_NW("falador_allotment_nw", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 12083)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFaladorPatchBounds(point);
		}
	},
	FALADOR_ALLOTMENT_SE("falador_allotment_se", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 12083)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFaladorPatchBounds(point);
		}
	},
	KOUREND_ALLOTMENT_NE("kourend_allotment_ne", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 6967, 6711),
	KOUREND_ALLOTMENT_SW("kourend_allotment_sw", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 6967, 6711),
	MORYTANIA_ALLOTMENT_NW("morytania_allotment_nw", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 14391, 14390),
	MORYTANIA_ALLOTMENT_SE("morytania_allotment_se", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 14391, 14390),
	PRIFDDINAS_ALLOTMENT_N("prifddinas_allotment_n", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
	PRIFDDINAS_ALLOTMENT_S("prifddinas_allotment_s", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_B, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
	HARMONY_ALLOTMENT("harmony_allotment", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_A, 15148),
	GUILD_ALLOTMENT_N("guild_allotment_n", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_C, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	GUILD_ALLOTMENT_S("guild_allotment_s", FarmingPatchType.ALLOTMENT,
		VarbitID.FARMING_TRANSMIT_D, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	ARDOUGNE_FLOWER("ardougne_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 10548),
	CATHERBY_FLOWER("catherby_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 11062, 11061, 11318, 11317)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInCatherbyAllotmentBounds(point);
		}
	},
	CIVITAS_FLOWER("civitas_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 6192, 6447, 6448, 6449, 6191, 6193),
	FALADOR_FLOWER("falador_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 12083)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFaladorPatchBounds(point);
		}
	},
	KOUREND_FLOWER("kourend_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 6967, 6711),
	MORYTANIA_FLOWER("morytania_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 14391, 14390),
	PRIFDDINAS_FLOWER("prifddinas_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
	GUILD_FLOWER("guild_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_H, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	KASTORI_FLOWER("kastori_flower", FarmingPatchType.FLOWER,
		VarbitID.FARMING_TRANSMIT_C, 5423, 5167, 5424),

	ARDOUGNE_HERB("ardougne_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D, 10548),
	CATHERBY_HERB("catherby_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D,
		11062, 11061, 11318, 11317)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInCatherbyAllotmentBounds(point);
		}
	},
	CIVITAS_HERB("civitas_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D,
		6192, 6447, 6448, 6449, 6191, 6193),
	FALADOR_HERB("falador_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D, 12083)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFaladorPatchBounds(point);
		}
	},
	FARMING_GUILD_HERB("guild_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_E,
		4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	HARMONY_HERB("harmony_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_B, 15148),
	KOUREND_HERB("kourend_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D, 6967, 6711),
	MORYTANIA_HERB("morytania_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D, 14391, 14390),
	TROLL_STRONGHOLD_HERB("troll_stronghold_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_A, 11321),
	WEISS_HERB("weiss_herb", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_A, 11325),

	AUBURNVALE_TREE("auburnvale_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		5427, 5428, 5684),
	FALADOR_TREE("falador_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		11828, 12084),
	GNOME_TREE("gnome_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		9781, 9782, 9526, 9525),
	LUMBRIDGE_TREE("lumbridge_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		12594, 12850),
	TAVERLEY_TREE("taverley_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		11573, 11829),
	VARROCK_TREE("varrock_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_A,
		12854, 12853),
	FARMING_GUILD_TREE("guild_tree", FarmingPatchType.TREE, VarbitID.FARMING_TRANSMIT_G,
		4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	BRIMHAVEN_FRUIT_TREE("brimhaven_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 11058, 11057),
	CATHERBY_FRUIT_TREE("catherby_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 11317)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return point.getX() >= 2840 || point.getY() < 3440 || point.getPlane() == 1;
		}
	},
	GNOME_FRUIT_TREE("gnome_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_B, 9781, 9782, 9526, 9525),
	KASTORI_FRUIT_TREE("kastori_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_B, 5423, 5167, 5424),
	LLETYA_FRUIT_TREE("lletya_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 9265, 11103),
	TREE_GNOME_VILLAGE_FRUIT_TREE("tree_gnome_village_fruit", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 9777, 10033),
	FARMING_GUILD_FRUIT_TREE("guild_fruit_tree", FarmingPatchType.FRUIT_TREE,
		VarbitID.FARMING_TRANSMIT_K, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	ANGLERS_HARDWOOD("anglers_hardwood", FarmingPatchType.HARDWOOD_TREE,
		VarbitID.FARMING_TRANSMIT_A, 9770),
	AVIUM_HARDWOOD("avium_hardwood", FarmingPatchType.HARDWOOD_TREE,
		VarbitID.FARMING_TRANSMIT_A, 6702, 6446),
	FOSSIL_HARDWOOD_EAST("fossil_hardwood_e", FarmingPatchType.HARDWOOD_TREE,
		VarbitID.FARMING_TRANSMIT_A, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFossilIslandBounds(point);
		}
	},
	FOSSIL_HARDWOOD_MIDDLE("fossil_hardwood_m", FarmingPatchType.HARDWOOD_TREE,
		VarbitID.FARMING_TRANSMIT_B, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFossilIslandBounds(point);
		}
	},
	FOSSIL_HARDWOOD_WEST("fossil_hardwood_w", FarmingPatchType.HARDWOOD_TREE,
		VarbitID.FARMING_TRANSMIT_C, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return isInFossilIslandBounds(point);
		}
	},

	GUILD_REDWOOD("guild_redwood", FarmingPatchType.REDWOOD, VarbitID.FARMING_TRANSMIT_I,
		4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	BRIMHAVEN_SPIRIT_TREE("brimhaven_spirit_tree", FarmingPatchType.SPIRIT_TREE,
		VarbitID.FARMING_TRANSMIT_B, 11058, 11057),
	ETCETERIA_SPIRIT_TREE("etceteria_spirit_tree", FarmingPatchType.SPIRIT_TREE,
		VarbitID.FARMING_TRANSMIT_B, 10300),
	PORT_SARIM_SPIRIT_TREE("port_sarim_spirit_tree", FarmingPatchType.SPIRIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 12082, 12083)
	{
		@Override
		boolean isInBounds(WorldPoint point)
		{
			return point.getY() < 3272;
		}
	},
	KOUREND_SPIRIT_TREE("kourend_spirit_tree", FarmingPatchType.SPIRIT_TREE,
		VarbitID.FARMING_TRANSMIT_F, 6967, 6711),
	GUILD_SPIRIT_TREE("guild_spirit_tree", FarmingPatchType.SPIRIT_TREE,
		VarbitID.FARMING_TRANSMIT_A, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	TAI_BWO_CALQUAT("tai_bwo_calquat", FarmingPatchType.CALQUAT,
		VarbitID.FARMING_TRANSMIT_A, 11056),
	KASTORI_CALQUAT("kastori_calquat", FarmingPatchType.CALQUAT,
		VarbitID.FARMING_TRANSMIT_A, 5423, 5167, 5424),
	GREAT_CONCH_CALQUAT("great_conch_calquat", FarmingPatchType.CALQUAT,
		VarbitID.FARMING_TRANSMIT_C, 12581, 12325, 12326, 12327, 12580, 12582, 12583,
		12836, 12837, 12838, 12839, 13092, 13093, 13194),
	GUILD_CELASTRUS("guild_celastrus", FarmingPatchType.CELASTRUS,
		VarbitID.FARMING_TRANSMIT_L, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	PRIFDDINAS_CRYSTAL_TREE("prifddinas_crystal_tree", FarmingPatchType.CRYSTAL_TREE,
		VarbitID.FARMING_TRANSMIT_E, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),

	ARDOUGNE_BUSH("ardougne_bush", FarmingPatchType.BUSH,
		VarbitID.FARMING_TRANSMIT_A, 10290, 10546),
	CHAMPIONS_BUSH("champions_bush", FarmingPatchType.BUSH,
		VarbitID.FARMING_TRANSMIT_A, 12596),
	ETCETERIA_BUSH("etceteria_bush", FarmingPatchType.BUSH,
		VarbitID.FARMING_TRANSMIT_A, 10300),
	RIMMINGTON_BUSH("rimmington_bush", FarmingPatchType.BUSH,
		VarbitID.FARMING_TRANSMIT_A, 11570, 11826),
	GUILD_BUSH("guild_bush", FarmingPatchType.BUSH, VarbitID.FARMING_TRANSMIT_B,
		4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),

	ALDARIN_HOPS("aldarin_hops", FarmingPatchType.HOPS, VarbitID.FARMING_TRANSMIT_A,
		5421, 5165, 5166, 5422, 5677, 5678),
	ENTRANA_HOPS("entrana_hops", FarmingPatchType.HOPS, VarbitID.FARMING_TRANSMIT_A,
		11060, 11316),
	LUMBRIDGE_HOPS("lumbridge_hops", FarmingPatchType.HOPS,
		VarbitID.FARMING_TRANSMIT_A, 12851),
	SEERS_HOPS("seers_hops", FarmingPatchType.HOPS, VarbitID.FARMING_TRANSMIT_A,
		10551, 10550),
	YANILLE_HOPS("yanille_hops", FarmingPatchType.HOPS,
		VarbitID.FARMING_TRANSMIT_A, 10288),

	VINERY_E1("vinery_e1", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_A1, 7223),
	VINERY_E2("vinery_e2", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_A2, 7223),
	VINERY_E3("vinery_e3", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_B1, 7223),
	VINERY_E4("vinery_e4", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_B2, 7223),
	VINERY_E5("vinery_e5", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_C1, 7223),
	VINERY_E6("vinery_e6", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_C2, 7223),
	VINERY_W1("vinery_w1", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_D1, 7223),
	VINERY_W2("vinery_w2", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_D2, 7223),
	VINERY_W3("vinery_w3", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_E1, 7223),
	VINERY_W4("vinery_w4", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_E2, 7223),
	VINERY_W5("vinery_w5", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_F1, 7223),
	VINERY_W6("vinery_w6", FarmingPatchType.GRAPES, VarbitID.FARMING_TRANSMIT_F2, 7223),

	MORYTANIA_MUSHROOM("morytania_mushroom", FarmingPatchType.MUSHROOM,
		VarbitID.FARMING_TRANSMIT_A, 13622, 13878),
	GUILD_HESPORI("guild_hespori", FarmingPatchType.HESPORI,
		VarbitID.FARMING_TRANSMIT_J, 5021),
	AUBURNVALE_BELLADONNA("auburnvale_belladonna", FarmingPatchType.BELLADONNA,
		VarbitID.FARMING_TRANSMIT_B, 5427, 5428, 5684),
	DRAYNOR_BELLADONNA("draynor_belladonna", FarmingPatchType.BELLADONNA,
		VarbitID.FARMING_TRANSMIT_A, 12340),
	AL_KHARID_CACTUS("al_kharid_cactus", FarmingPatchType.CACTUS,
		VarbitID.FARMING_TRANSMIT_A, 13106, 13362, 13105),
	GUILD_CACTUS("guild_cactus", FarmingPatchType.CACTUS,
		VarbitID.FARMING_TRANSMIT_F, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	GUILD_ANIMA("guild_anima", FarmingPatchType.ANIMA,
		VarbitID.FARMING_TRANSMIT_M, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
	GREAT_CONCH_CORAL_E("great_conch_coral_e", FarmingPatchType.CORAL,
		VarbitID.FARMING_TRANSMIT_A, 12581, 12325, 12326, 12327, 12580, 12582, 12583,
		12836, 12837, 12838, 12839, 13092, 13093, 13194),
	GREAT_CONCH_CORAL_W("great_conch_coral_w", FarmingPatchType.CORAL,
		VarbitID.FARMING_TRANSMIT_B, 12581, 12325, 12326, 12327, 12580, 12582, 12583,
		12836, 12837, 12838, 12839, 13092, 13093, 13194),
	SEAWEED_N("seaweed_n", FarmingPatchType.SEAWEED, VarbitID.FARMING_TRANSMIT_A, 15008),
	SEAWEED_S("seaweed_s", FarmingPatchType.SEAWEED, VarbitID.FARMING_TRANSMIT_B, 15008);

	private static final Map<Integer, List<FarmingPatchLocation>> BY_REGION = new HashMap<>();

	static
	{
		for (FarmingPatchLocation location : values())
		{
			for (int regionId : location.regionIds)
			{
				BY_REGION.computeIfAbsent(regionId, ignored -> new ArrayList<>()).add(location);
			}
		}
		BY_REGION.replaceAll((ignored, locations) -> Collections.unmodifiableList(locations));
	}

	private final String wireName;
	private final FarmingPatchType patchType;
	private final int varbitId;
	private final int[] regionIds;

	FarmingPatchLocation(
		String wireName,
		FarmingPatchType patchType,
		int varbitId,
		int... regionIds)
	{
		this.wireName = wireName;
		this.patchType = patchType;
		this.varbitId = varbitId;
		this.regionIds = regionIds;
	}

	static List<FarmingPatchLocation> forWorldPoint(WorldPoint point)
	{
		List<FarmingPatchLocation> candidates = BY_REGION.get(point.getRegionID());
		if (candidates == null)
		{
			return Collections.emptyList();
		}
		List<FarmingPatchLocation> matches = new ArrayList<>(candidates.size());
		for (FarmingPatchLocation candidate : candidates)
		{
			if (candidate.isInBounds(point))
			{
				matches.add(candidate);
			}
		}
		return matches;
	}

	String getWireName()
	{
		return wireName;
	}

	FarmingPatchType getPatchType()
	{
		return patchType;
	}

	int getVarbitId()
	{
		return varbitId;
	}

	boolean isInBounds(WorldPoint point)
	{
		return true;
	}

	private static boolean isInFossilIslandBounds(WorldPoint point)
	{
		if (point.getPlane() != 0)
		{
			return false;
		}
		if (point.getX() == 3753 && point.getY() >= 3868 && point.getY() <= 3870)
		{
			return false;
		}
		return !((point.getX() == 3729
			|| point.getX() == 3728
			|| point.getX() == 3747
			|| point.getX() == 3746)
			&& point.getY() >= 3830
			&& point.getY() <= 3832);
	}

	private static boolean isInCatherbyAllotmentBounds(WorldPoint point)
	{
		if (point.getX() >= 2816 && point.getY() < 3456)
		{
			return point.getX() < 2840 && point.getY() >= 3440 && point.getPlane() == 0;
		}
		return true;
	}

	private static boolean isInFaladorPatchBounds(WorldPoint point)
	{
		return point.getY() >= 3272;
	}
}

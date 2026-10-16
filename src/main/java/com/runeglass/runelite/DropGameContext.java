package com.runeglass.runelite;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

final class DropGameContext
{
	static final int COLLECTION_LOG_NOTIFICATION = VarbitID.OPTION_COLLECTION_NEW_ITEM;
	private String family;
	private String encounter;

	static String collectionLogSetting(int value)
	{
		if (value == 1 || value == 3) return "enabled";
		if (value == 0 || value == 2) return "disabled";
		return "unknown";
	}

	static String collectionLogSetting(Client client)
	{
		try { return collectionLogSetting(client.getVarbitValue(COLLECTION_LOG_NOTIFICATION)); }
		catch (RuntimeException exception) { return "unknown"; }
	}

	private static final Set<Integer> COX_REGIONS = Set.of(12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145, 13393, 13394, 13395, 13396, 13397, 13401);
	private static final Set<Integer> TOB_REGIONS = Set.of(12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
	private static final Set<Integer> TOA_REGIONS = Set.of(14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700);

	String raidEncounter(Client client)
	{
		if (client.getTopLevelWorldView() == null || !client.getTopLevelWorldView().isInstance() || client.getLocalPlayer() == null)
			return update(null);
		int region = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
		String next = raidFamily(region, client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON),
			client.getVarbitValue(VarbitID.RAIDS_CLIENT_PROGRESS), client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS),
			client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS));
		return update(next);
	}

	static String raidFamily(int region, int inCox, int coxProgress, int tobStatus, int toaStatus)
	{
		if (COX_REGIONS.contains(region) && inCox > 0 && coxProgress > 0) return "Chambers of Xeric";
		if (TOB_REGIONS.contains(region) && tobStatus >= 2) return "Theatre of Blood";
		if (TOA_REGIONS.contains(region) && toaStatus >= 2) return "Tombs of Amascut";
		return null;
	}

	String update(String next)
	{
		if (!Objects.equals(family, next))
		{
			family = next;
			encounter = next == null ? null : next + ":" + UUID.randomUUID();
		}
		return encounter;
	}

	void clear() { family = null; encounter = null; }
}

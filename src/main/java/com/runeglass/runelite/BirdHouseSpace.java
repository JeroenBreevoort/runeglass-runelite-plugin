package com.runeglass.runelite;

import net.runelite.api.gameval.VarPlayerID;

enum BirdHouseSpace
{
	MEADOW_NORTH("meadow_north", VarPlayerID.BIRDHOUSE_TRANSMIT_A),
	MEADOW_SOUTH("meadow_south", VarPlayerID.BIRDHOUSE_TRANSMIT_B),
	VALLEY_NORTH("valley_north", VarPlayerID.BIRDHOUSE_TRANSMIT_C),
	VALLEY_SOUTH("valley_south", VarPlayerID.BIRDHOUSE_TRANSMIT_D);

	private final String wireValue;
	private final int varpId;

	BirdHouseSpace(String wireValue, int varpId)
	{
		this.wireValue = wireValue;
		this.varpId = varpId;
	}

	String wireValue()
	{
		return wireValue;
	}

	int getVarpId()
	{
		return varpId;
	}
}

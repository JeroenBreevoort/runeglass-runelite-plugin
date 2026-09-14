package com.runeglass.runelite;

enum BirdHouseState
{
	EMPTY("empty"),
	BUILT("built"),
	SEEDED("seeded");

	private final String wireValue;

	BirdHouseState(String wireValue)
	{
		this.wireValue = wireValue;
	}

	String wireValue()
	{
		return wireValue;
	}

	static BirdHouseState fromVarp(int varp)
	{
		if (varp == 0)
		{
			return EMPTY;
		}
		return varp % 3 == 0 ? SEEDED : BUILT;
	}
}

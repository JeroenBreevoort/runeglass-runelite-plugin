package com.runeglass.runelite;

enum BirdHouseTier
{
	NORMAL("normal"),
	OAK("oak"),
	WILLOW("willow"),
	TEAK("teak"),
	MAPLE("maple"),
	MAHOGANY("mahogany"),
	YEW("yew"),
	MAGIC("magic"),
	REDWOOD("redwood");

	private final String wireValue;

	BirdHouseTier(String wireValue)
	{
		this.wireValue = wireValue;
	}

	String wireValue()
	{
		return wireValue;
	}

	static BirdHouseTier fromVarp(int varp)
	{
		return values()[(varp - 1) / 3];
	}
}

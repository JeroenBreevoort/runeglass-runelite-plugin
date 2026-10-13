package com.runeglass.runelite;

import java.util.Set;

final class DropPolicy
{
	static final long MINIMUM_VALUE = 50000L;
	static final Set<String> REWARD_EVENTS = Set.of(
		"Barrows",
		"Chambers of Xeric",
		"Theatre of Blood",
		"Tombs of Amascut",
		"Clue Scroll (Beginner)",
		"Clue Scroll (Easy)",
		"Clue Scroll (Medium)",
		"Clue Scroll (Hard)",
		"Clue Scroll (Elite)",
		"Clue Scroll (Master)");

	private DropPolicy()
	{
	}

	static boolean qualifies(String type, String name, int itemId, Long capturedStackGp)
	{
		return (capturedStackGp != null && capturedStackGp >= MINIMUM_VALUE
			&& capturedStackGp <= 9007199254740991L) || isNotableReward(type, name, itemId);
	}

	static boolean isNotableReward(String type, String name, int itemId)
	{
		if ("npc".equals(type) && "Abyssal demon".equals(name))
		{
			return itemId == 4151;
		}
		if ("npc".equals(type) && "Greater abyssal demon".equals(name))
		{
			return itemId == 4151;
		}
		if ("npc".equals(type) && "Vorkath".equals(name))
		{
			return itemId == 11286 || itemId == 21907 || itemId == 22006;
		}
		if ("npc".equals(type) && "Zulrah".equals(name))
		{
			return itemId == 12922 || itemId == 12927 || itemId == 12932 || itemId == 13200 || itemId == 13201;
		}
		if ("event".equals(type) && "Clue Scroll (Medium)".equals(name))
		{
			return itemId == 2577 || itemId == 2579;
		}
		if ("event".equals(type) && "Chambers of Xeric".equals(name))
		{
			return itemId == 20997 || itemId == 21003 || itemId == 21012 || itemId == 21015 || itemId == 21018 || itemId == 21021 || itemId == 21024 || itemId == 21034 || itemId == 21079;
		}
		return false;
	}
}

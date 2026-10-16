package com.runeglass.runelite;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropCountParserTest
{
	@Test public void upstreamFormatsNormalizeMarkupCommasAndPunctuation()
	{
		assertCount("Your <col=ff0000>Kree'arra</col> kill count is:<col=ff0000>1,130</col>.", "kreearra", 1130, "kill");
		assertCount("Your K'ril Tsutsaroth kill count is: @mes_hl_red@2</col>.", "kril-tsutsaroth", 2, "kill");
		assertCount("Your Nightmare kill count is: 12", "nightmare", 12, "kill");
		assertCount("Your Barrows chest count is: 277.", "barrows-chests", 277, "chest");
		assertCount("Your Corrupted Gauntlet completion count is: 4729.", "the-corrupted-gauntlet", 4729, "completion");
		assertCount("Your subdued Wintertodt count is: 4.", "wintertodt", 4, "completion");
		assertCount("Your Tempoross kill count is: 60.", "tempoross", 60, "completion");
	}

	@Test public void everyRegisteredCounterHasAParserPathWithoutDependingOnRatedItems()
	{
		for (DropCounterRegistry.Counter counter : DropCounterRegistry.COUNTERS)
		{
			String source = counter.aliases.get(0);
			assertCount("Your completion count for " + source + " is: 123.", counter.key, 123, counter.unit);
		}
	}

	@Test public void modeAndTierStayDistinct()
	{
		assertCount("Your completed Chambers of Xeric Challenge Mode count is: 13.", "chambers-of-xeric-challenge-mode", 13, "completion");
		assertCount("Your completed Theatre of Blood: Entry Mode count is: 73.", "theatre-of-blood-entry-mode", 73, "completion");
		assertCount("Your completed Theatre of Blood: Hard Mode count is: 23.", "theatre-of-blood-hard-mode", 23, "completion");
		for (String mode : new String[]{"", ": Entry Mode", ": Expert Mode"})
			assertCount("Your completed Tombs of Amascut" + mode + " count is: 3.", DropCounterRegistry.normalize("Tombs of Amascut" + mode), 3, "completion");
		for (String tier : new String[]{"beginner", "easy", "medium", "hard", "elite", "master"})
		{
			assertCount("You have completed 1 " + tier + " Treasure Trail.", "clue-scrolls-" + tier, 1, "casket");
			assertCount("You have completed 1,234 " + tier + " Treasure Trails.", "clue-scrolls-" + tier, 1234, "casket");
		}
	}

	@Test public void rejectsUnknownSeasonalAndMalformedMessages()
	{
		for (String message : new String[]{
			"Your Vorkath count is: 4.", "Your loot is worth 4 coins.", "Other player: Your Vorkath kill count is: 4.",
			"Your Corrupted Hunllef (Echo) kill count is: 4.", "Your completed Theatre of Blood: Story Mode count is: 4.",
			"Your Vorkath kill count is: 1,23.", "Your Vorkath kill count is: -4.", "Your Vorkath kill count is: 2.5.",
			"Your Vorkath kill count is: 0.", "Your Vorkath kill count is: 2147483648.", "Your mystery kill count is: 4.",
			"You have completed 2 all Treasure Trails."}) assertNull(message, DropCountParser.parse(message));
	}

	@Test public void collectionLogSettingAndItemAreFactsOnly()
	{
		assertEquals("Skeletal visage", DropCountParser.collectionItem("<col=ff0000>New item added to your collection log: Skeletal visage</col>"));
		assertNull(DropCountParser.collectionItem("Other player received Skeletal visage"));
		for (int value : new int[]{1, 3}) assertEquals("enabled", DropGameContext.collectionLogSetting(value));
		for (int value : new int[]{0, 2}) assertEquals("disabled", DropGameContext.collectionLogSetting(value));
		assertEquals("unknown", DropGameContext.collectionLogSetting(-1));
		assertEquals("unknown", DropGameContext.collectionLogSetting(4));
	}

	private static void assertCount(String message, String key, int value, String unit)
	{
		DropCountParser.Count count = DropCountParser.parse(message);
		assertNotNull(message, count);
		assertEquals(key, count.counter.key); assertEquals(value, count.value); assertEquals(unit, count.counter.unit);
	}
}

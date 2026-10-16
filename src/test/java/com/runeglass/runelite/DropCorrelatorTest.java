package com.runeglass.runelite;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropCorrelatorTest
{
	private final DropCorrelator correlator = new DropCorrelator();
	private final List<DropCorrelator.Completed> completed = new ArrayList<>();
	private static final long SECOND = 1_000_000_000L;

	@Test public void reviewedLootSourceNamesCorrelateWithTheirOwnCounters()
	{
		for (String[] pair : new String[][]{
			{"Obor (Chest)", "obor", "chest"}, {"Bryophyta (Chest)", "bryophyta", "chest"},
			{"Dusk", "grotesque-guardians", "kill"}, {"Shellbane gryphon", "shellbane-gryphon", "kill"},
			{"Crazy archaeologist", "crazy-archaeologist", "kill"}, {"Deranged archaeologist", "deranged-archaeologist", "kill"},
			{"Thermonuclear smoke devil", "thermonuclear-smoke-devil", "kill"}})
		{
			correlator.clear(); completed.clear();
			count(pair[1], 123, 10, null);
			correlator.loot(drop("npc", pair[0], true), 10, 10 * SECOND, null, completed::add);
			correlator.advance(21, 21 * SECOND, completed::add);
			assertEquals(1, completed.size());
			assertNotNull(pair[0], completed.get(0).count);
			assertEquals(pair[1], completed.get(0).count.counter.key);
			assertEquals(pair[2], completed.get(0).count.counter.unit);
		}
	}

	@Test public void countAndCollectionSlotMatchBothEventOrdersAndFreezeOriginalFacts()
	{
		for (boolean countFirst : new boolean[]{true, false})
		{
			correlator.clear(); completed.clear(); correlator.context(0, "enabled", null);
			DropCaptureResult drop = drop("npc", "Vorkath", true);
			if (countFirst)
			{
				count("vorkath", 1200, 10, null);
				correlator.collection("Skeletal visage", 10, 10 * SECOND);
			}
			correlator.loot(drop, 10, 10 * SECOND, null, completed::add);
			if (!countFirst)
			{
				correlator.collection("Skeletal visage", 11, 11 * SECOND);
				count("vorkath", 1200, 11, null);
			}
			correlator.advance(20, 20 * SECOND, completed::add);
			assertTrue(completed.isEmpty());
			correlator.advance(21, 21 * SECOND, completed::add);
			DropCorrelator.Completed result = completed.get(0);
			assertEquals(1200, result.count.value); assertEquals("enabled", result.collectionLog); assertEquals(Set.of(22006), result.newSlots);
			DropEvent frozen = drop.event().get().freeze(result.count, null, result.collectionLog, result.newSlots);
			assertEquals(drop.event().get().eventId(), frozen.eventId());
			JsonObject event = DropCaptureTest.event(frozen.envelope(DropCaptureTest.CONTEXT, "connection"));
			assertEquals(DropCaptureTest.NOW.toString(), event.get("occurredAt").getAsString());
			assertTrue(event.getAsJsonArray("items").get(0).getAsJsonObject().get("newCollectionLogSlot").getAsBoolean());
			assertThrows(IllegalStateException.class, () -> frozen.freeze(null, null, "unknown", Set.of()));
		}
	}

	@Test public void lateCompetitorKeepsBothCountsUnknownInsteadOfGreedyMatching()
	{
		correlator.context(0, "enabled", null);
		correlator.loot(drop("npc", "Vorkath", true), 10, 10 * SECOND, null, completed::add);
		count("vorkath", 100, 15, null);
		correlator.advance(16, 16 * SECOND, completed::add);
		assertTrue(completed.isEmpty());
		correlator.loot(drop("npc", "Vorkath", true), 20, 20 * SECOND, null, completed::add);
		correlator.advance(21, 21 * SECOND, completed::add);
		correlator.advance(31, 31 * SECOND, completed::add);
		assertEquals(2, completed.size());
		completed.forEach(result -> assertNull(result.count));
	}

	@Test public void multipleCountsAreAmbiguousButDifferentBossesStayIndependent()
	{
		correlator.loot(drop("npc", "Vorkath", true), 10, 10 * SECOND, null, completed::add);
		count("vorkath", 100, 10, null); count("vorkath", 101, 11, null);
		correlator.loot(drop("npc", "Zulrah", true), 10, 10 * SECOND, null, completed::add);
		count("zulrah", 42, 10, null); count("zulrah", 42, 10, null);
		correlator.advance(21, 21 * SECOND, completed::add);
		assertNull(completed.get(0).count); assertEquals(42, completed.get(1).count.value);
	}

	@Test public void delayedRaidTokenRequiresSameEncounterModeAndIsConsumedOnce()
	{
		String encounter = "Chambers of Xeric:encounter-one";
		correlator.context(0, "enabled", encounter);
		count("chambers-of-xeric-challenge-mode", 13, 10, encounter);
		correlator.advance(500, 500 * SECOND, completed::add);
		correlator.loot(drop("event", "Chambers of Xeric", true), 500, 500 * SECOND, encounter, completed::add);
		correlator.advance(511, 511 * SECOND, completed::add);
		assertEquals("chambers-of-xeric-challenge-mode", completed.get(0).count.counter.key);
		correlator.loot(drop("event", "Chambers of Xeric", true), 520, 520 * SECOND, encounter, completed::add);
		correlator.advance(531, 531 * SECOND, completed::add);
		assertNull(completed.get(1).count);
		correlator.context(532, "enabled", null);
		correlator.context(533, "enabled", "Chambers of Xeric:encounter-two");
		correlator.loot(drop("event", "Chambers of Xeric", true), 534, 534 * SECOND, "Chambers of Xeric:encounter-two", completed::add);
		correlator.advance(545, 545 * SECOND, completed::add);
		assertNull(completed.get(2).count);
	}

	@Test public void raidAfterLootIsSupportedAndMissingContextCannotBorrowACount()
	{
		String encounter = "Tombs of Amascut:run";
		correlator.context(0, "enabled", encounter);
		correlator.loot(drop("event", "Tombs of Amascut", true), 10, 10 * SECOND, encounter, completed::add);
		count("tombs-of-amascut-expert-mode", 42, 11, encounter);
		correlator.advance(21, 21 * SECOND, completed::add);
		assertEquals(42, completed.get(0).count.value);
		correlator.clear(); completed.clear();
		count("tombs-of-amascut", 1, 10, null);
		correlator.loot(drop("event", "Tombs of Amascut", true), 10, 10 * SECOND, null, completed::add);
		correlator.advance(21, 21 * SECOND, completed::add);
		assertNull(completed.get(0).count);
	}

	@Test public void barrowsAndClueTiersUseIndependentCountersAndLowRewardsStillObserveCounts()
	{
		for (String key : new String[]{"barrows-chests", "clue-scrolls-medium", "clue-scrolls-hard"})
		{
			DropCounterRegistry.Counter counter = DropCounterRegistry.byKey(key);
			String source = key.startsWith("clue") ? counter.sources.get(1) : "Barrows";
			count(key, 12, 10, null);
			correlator.loot(drop("event", source, false), 11, 11 * SECOND, null, completed::add);
		}
		correlator.advance(22, 22 * SECOND, completed::add);
		assertEquals(3, completed.size());
		for (DropCorrelator.Completed result : completed) { assertEquals(12, result.count.value); assertFalse(result.result.event().isPresent()); }
	}

	@Test public void expiredRaidTokensAndInsufficientTicksCannotProduceEvidence()
	{
		String encounter = "Theatre of Blood:run";
		correlator.context(0, "enabled", encounter);
		count("theatre-of-blood-hard-mode", 50, 10, encounter);
		correlator.loot(drop("event", "Theatre of Blood", true), 1811, 1811 * SECOND, encounter, completed::add);
		correlator.advance(1822, 1822 * SECOND, completed::add);
		assertNull(completed.get(0).count);
		correlator.clear(); completed.clear(); correlator.context(0, "enabled", null);
		count("vorkath", 100, 10, null);
		correlator.collection("Skeletal visage", 10, 10 * SECOND);
		correlator.loot(drop("npc", "Vorkath", true), 10, 10 * SECOND, null, completed::add);
		correlator.advance(11, 23 * SECOND, completed::add);
		assertNull(completed.get(0).count);
		assertTrue(completed.get(0).newSlots.isEmpty());
		assertEquals("unknown", completed.get(0).collectionLog);
	}

	@Test public void aggregatedRewardsCannotBorrowASingleAttemptCount()
	{
		DropEvent original = drop("event", "Barrows", true).event().get();
		JsonObject item = DropCaptureTest.event(original.envelope(DropCaptureTest.CONTEXT, "connection"))
			.getAsJsonArray("items").get(0).getAsJsonObject();
		DropEvent aggregate = new DropEvent(UUID.randomUUID(), DropCaptureTest.SESSION, DropCaptureTest.NOW,
			"event", "Barrows", 2, List.of(item));
		count("barrows-chests", 100, 10, null);
		correlator.loot(DropCaptureResult.observed("event", 1, 0, null, true, aggregate, true),
			10, 10 * SECOND, null, completed::add);
		correlator.advance(21, 21 * SECOND, completed::add);
		assertNull(completed.get(0).count);
	}

	@Test public void missingMessageTimeoutLogoutAndAmbiguousLogDoNotInventEvidence()
	{
		correlator.context(0, "enabled", null);
		correlator.loot(drop("npc", "Vorkath", true), 10, 10 * SECOND, null, completed::add);
		correlator.loot(drop("npc", "Zulrah", true), 10, 10 * SECOND, null, completed::add);
		correlator.collection("Skeletal visage", 10, 10 * SECOND);
		correlator.advance(21, 21 * SECOND, completed::add);
		for (DropCorrelator.Completed result : completed) { assertNull(result.count); assertTrue(result.newSlots.isEmpty()); assertEquals("unknown", result.collectionLog); }
		correlator.loot(drop("npc", "Vorkath", true), 30, 30 * SECOND, null, completed::add);
		count("vorkath", 123, 30, null);
		correlator.flush(completed::add);
		assertNull(completed.get(2).count); assertEquals("unknown", completed.get(2).collectionLog);
	}

	@Test public void settingChangesAndOverflowWithholdNegativeEvidence()
	{
		correlator.context(0, "enabled", null);
		correlator.loot(drop("npc", "Vorkath", true), 10, 10 * SECOND, null, completed::add);
		correlator.context(11, "disabled", null);
		correlator.advance(21, 21 * SECOND, completed::add);
		assertEquals("unknown", completed.get(0).collectionLog);
		for (int i = 0; i < 65; i++) correlator.loot(drop("npc", "Vorkath", true), 30, 30 * SECOND, null, completed::add);
		correlator.advance(41, 41 * SECOND, completed::add);
		assertEquals(66, completed.size());
		completed.forEach(result -> assertNull(result.count));
	}

	private void count(String key, int value, int tick, String encounter)
	{ correlator.count(new DropCountParser.Count(DropCounterRegistry.byKey(key), value), tick, tick * SECOND, encounter); }

	static DropCaptureResult drop(String type, String source, boolean retained)
	{
		JsonObject item = new JsonObject(); item.addProperty("itemId", 22006); item.addProperty("itemName", "Skeletal visage"); item.addProperty("quantity", 1);
		JsonObject value = new JsonObject(); value.addProperty("kind", "unknown"); item.add("value", value);
		DropEvent event = new DropEvent(UUID.randomUUID(), DropCaptureTest.SESSION, DropCaptureTest.NOW, type, source, 1, List.of(item));
		return DropCaptureResult.observed(type, 1, 0, null, retained, event, retained);
	}
}

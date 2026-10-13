package com.runeglass.runelite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropCaptureTest
{
	static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
	static final UUID SESSION = UUID.fromString("0b54873e-c169-4de7-9bd0-abc879f84d2f");
	static final SyncContext CONTEXT = new SyncContext(SESSION.toString(), "Fixture", "regular", "standard", "1.2.0", "1.13.1", 231);

	@Test public void ordinaryStackRequiresAtLeastFiftyThousandCapturedGp()
	{
		for (long price : new long[]{49_999, 50_000})
		{
			assertEquals(price == 50_000, new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1)),
				id -> definition(id, true, -1, -1), id -> price, SESSION, NOW).event().isPresent());
		}
	}

	@Test public void quantityAndMergedSameItemStacksCanReachTheThreshold()
	{
		for (ItemStack[] stacks : List.of(new ItemStack[]{new ItemStack(10000, 2)},
			new ItemStack[]{new ItemStack(10000, 1), new ItemStack(10000, 1)}))
		{
			DropEvent drop = new DropCapture().capture(loot("Guard", LootRecordType.PICKPOCKET, stacks),
				id -> definition(id, true, -1, -1), id -> 25_000, SESSION, NOW).event().get();
			JsonArray items = event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items");
			assertEquals(1, items.size());
			assertEquals(2, items.get(0).getAsJsonObject().get("quantity").getAsInt());
			assertEquals(25_000, items.get(0).getAsJsonObject().getAsJsonObject("value").get("unitPrice").getAsInt());
		}
	}

	@Test public void coinQuantityUsesTheSameInclusiveThresholdWithoutAPriceLookup()
	{
		for (int quantity : new int[]{49_999, 50_000})
		{
			assertEquals(quantity == 50_000, new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(995, quantity)),
				id -> definition(id, true, -1, -1), id -> { throw new AssertionError(); }, SESSION, NOW).event().isPresent());
		}
	}

	@Test public void differentLowStacksDoNotQualifyByTheirCombinedTotal()
	{
		assertFalse(new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1), new ItemStack(10001, 1)),
			id -> definition(id, true, -1, -1), id -> 30_000, SESSION, NOW).event().isPresent());
	}

	@Test public void notableRewardsQualifyWithUnknownOrUntradeableValue()
	{
		for (boolean tradeable : new boolean[]{false, true})
		{
			DropEvent drop = new DropCapture().capture(loot("Vorkath", LootRecordType.NPC, new ItemStack(21907, 1)),
				id -> definition(id, tradeable, -1, -1), id -> 0, SESSION, NOW).event().get();
			JsonObject value = event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("value");
			assertEquals(tradeable ? "unknown" : "untradeable", value.get("kind").getAsString());
			assertFalse(value.has("unitPrice"));
		}
	}

	@Test public void notableMatchingRequiresExactSourceTypeNameAndObservedId()
	{
		for (LootReceived loot : List.of(
			loot("Zulrah", LootRecordType.NPC, new ItemStack(21907, 1)),
			loot("Vorkath", LootRecordType.PICKPOCKET, new ItemStack(21907, 1)),
			loot("Clue Scroll (Medium)", LootRecordType.NPC, new ItemStack(2577, 1)),
			loot("Clue Scroll (Hard)", LootRecordType.EVENT, new ItemStack(2577, 1)),
			loot("Vorkath", LootRecordType.NPC, new ItemStack(21908, 1))))
		{
			assertFalse(new DropCapture().capture(loot,
				id -> definition(id, true, id == 21908 ? 799 : -1, id == 21908 ? 21907 : -1), id -> 0, SESSION, NOW).event().isPresent());
		}
		assertTrue(new DropCapture().capture(loot("Clue Scroll (Medium)", LootRecordType.EVENT, new ItemStack(2577, 1)),
			id -> definition(id, true, -1, -1), id -> 0, SESSION, NOW).event().isPresent());
	}

	@Test public void ordinaryUnknownOrUntradeableItemsDoNotQualify()
	{
		for (boolean tradeable : new boolean[]{false, true})
		{
			for (long price : new long[]{0, -1, (long) Integer.MAX_VALUE + 1})
			{
				assertFalse(new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1)),
					id -> definition(id, tradeable, -1, -1), id -> price, SESSION, NOW).event().isPresent());
			}
		}
	}

	@Test(expected = IllegalArgumentException.class) public void qualifyingFirstItemDoesNotBypassInvalidSiblingDefinition()
	{
		new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1), new ItemStack(20000, 1)),
			id -> id == 10000 ? definition(id, true, -1, -1) : null, id -> 50_000, SESSION, NOW);
	}

	@Test(expected = IllegalArgumentException.class) public void qualifyingFirstItemDoesNotBypassSiblingValueOverflow()
	{
		new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1), new ItemStack(20000, Integer.MAX_VALUE)),
			id -> definition(id, true, -1, -1), id -> id == 10000 ? 50_000 : Integer.MAX_VALUE, SESSION, NOW);
	}

	@Test(expected = IllegalArgumentException.class) public void lowValueFilterDoesNotHideInvalidSiblingDefinition()
	{
		new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1), new ItemStack(20000, 1)),
			id -> id == 10000 ? definition(id, true, -1, -1) : null, id -> 1, SESSION, NOW);
	}

	@Test public void repeatedIdenticalRewardsKeepSeparateIdsButRepeatedObjectIsIgnored()
	{
		DropCapture capture = new DropCapture();
		LootReceived first = loot("Abyssal demon", LootRecordType.NPC, new ItemStack(4151, 1));
		LootReceived second = loot("Abyssal demon", LootRecordType.NPC, new ItemStack(4151, 1));
		JsonObject left = capture.capture(first, id -> definition(id, true, -1, -1), id -> 10, SESSION, NOW).event().get().envelope(CONTEXT, "connection");
		JsonObject right = capture.capture(second, id -> definition(id, true, -1, -1), id -> 10, SESSION, NOW).event().get().envelope(CONTEXT, "connection");
		assertNotEquals(event(left).get("eventId"), event(right).get("eventId"));
		assertFalse(capture.capture(first, id -> definition(id, true, -1, -1), id -> 10, SESSION, NOW).event().isPresent());
		assertEquals(NOW.toString(), event(left).get("occurredAt").getAsString());
		assertEquals(3, event(left).get("rollCount").getAsInt());
		assertFalse(left.toString().contains("metadata"));
	}

	@Test public void consolidatesStacksAndPreservesUnknownUntradeableAndExactCoins()
	{
		LootReceived loot = loot("Barrows", LootRecordType.EVENT,
			new ItemStack(995, 11), new ItemStack(995, 22), new ItemStack(4151, 2), new ItemStack(2, 1), new ItemStack(3, 1), new ItemStack(30000, 1));
		DropEvent drop = new DropCapture().capture(loot, id -> definition(id, id != 2, -1, -1), id -> id == 30000 ? 50_000 : id == 3 ? 90 : 0, SESSION, NOW).event().get();
		JsonArray items = event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items");
		assertEquals(5, items.size());
		assertEquals("untradeable", items.get(0).getAsJsonObject().getAsJsonObject("value").get("kind").getAsString());
		assertFalse(items.get(0).getAsJsonObject().has("marketItemId"));
		assertEquals(90, items.get(1).getAsJsonObject().getAsJsonObject("value").get("unitPrice").getAsInt());
		assertEquals(3, items.get(1).getAsJsonObject().get("marketItemId").getAsInt());
		assertEquals("coins", items.get(2).getAsJsonObject().getAsJsonObject("value").get("kind").getAsString());
		assertFalse(items.get(2).getAsJsonObject().has("marketItemId"));
		assertEquals(33, items.get(2).getAsJsonObject().get("quantity").getAsInt());
		assertEquals("unknown", items.get(3).getAsJsonObject().getAsJsonObject("value").get("kind").getAsString());
		assertEquals(4151, items.get(3).getAsJsonObject().get("marketItemId").getAsInt());
	}

	@Test public void valuesNotedItemsUsingUnnotedTradeabilityAndPrice()
	{
		Map<Integer, ItemComposition> definitions = Map.of(4152, definition(4152, false, 799, 4151), 4151, definition(4151, true, -1, -1), 30000, definition(30000, true, -1, -1));
		DropEvent drop = new DropCapture().capture(loot("Abyssal demon", LootRecordType.NPC, new ItemStack(4152, 2), new ItemStack(30000, 1)), definitions::get,
			id -> { if (id == 30000) return 50_000; assertEquals(4151, id); return 123; }, SESSION, NOW).event().get();
		JsonObject item = event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals(4152, item.get("itemId").getAsInt());
		assertEquals(4151, item.get("marketItemId").getAsInt());
		assertEquals(123, item.getAsJsonObject("value").get("unitPrice").getAsInt());
	}

	@Test public void retainsProvenNotedMarketIdWithoutACapturedPrice()
	{
		for (long price : new long[]{0, -1, (long) Integer.MAX_VALUE + 1})
		{
			JsonObject item = event(notedDrop(price).envelope(CONTEXT, "connection")).getAsJsonArray("items").get(0).getAsJsonObject();
			assertEquals(22125, item.get("itemId").getAsInt());
			assertEquals(22124, item.get("marketItemId").getAsInt());
			assertEquals("unknown", item.getAsJsonObject("value").get("kind").getAsString());
			assertFalse(item.getAsJsonObject("value").has("unitPrice"));
		}
	}

	@Test public void omitsMarketIdWhenLinkedDefinitionIsMissingInvalidOrCoins()
	{
		for (int linkedId : new int[]{22124, 995, 0})
		{
			DropEvent drop = new DropCapture().capture(loot("Vorkath", LootRecordType.NPC, new ItemStack(22125, 2), new ItemStack(30000, 1)),
				id -> id == 30000 ? definition(id, true, -1, -1) : id == 22125 ? definition(id, false, 799, 22124)
					: linkedId == 22124 ? null : definition(linkedId, true, -1, -1),
				id -> { if (id == 30000) return 50_000; throw new AssertionError("Unresolved items must not be priced"); }, SESSION, NOW).event().get();
			JsonObject item = event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items").get(0).getAsJsonObject();
			assertEquals(22125, item.get("itemId").getAsInt());
			assertFalse(item.has("marketItemId"));
			assertEquals("unknown", item.getAsJsonObject("value").get("kind").getAsString());
		}
	}

	@Test public void excludesPlayerLootAndUnreviewedEvents()
	{
		DropCapture capture = new DropCapture();
		for (LootReceived loot : List.of(loot("Other Player", LootRecordType.PLAYER, new ItemStack(995, 1)), loot("Unreviewed label", LootRecordType.EVENT, new ItemStack(995, 1))))
		{
			assertFalse(capture.capture(loot, id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW).event().isPresent());
		}
	}

	@Test(expected = ArithmeticException.class) public void rejectsQuantityOverflow()
	{
		new DropCapture().capture(loot("Barrows", LootRecordType.EVENT, new ItemStack(995, Integer.MAX_VALUE), new ItemStack(995, 1)),
			id -> definition(id, true, -1, -1), id -> 1, SESSION, NOW);
	}

	@Test public void rejectsOtherPluginInjectionAndRetainsOutOfRangePricesAsUnknown()
	{
		LootReceived injected = loot("Barrows", LootRecordType.EVENT, new ItemStack(995, 1));
		injected.setMetadata(List.of(new ItemStack(995, 1)));
		assertFalse(new DropCapture().capture(injected, id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW).event().isPresent());
		DropEvent drop = new DropCapture().capture(loot("Abyssal demon", LootRecordType.NPC, new ItemStack(4151, 1)),
			id -> definition(id, true, -1, -1), id -> (long) Integer.MAX_VALUE + 1, SESSION, NOW).event().get();
		assertEquals("unknown", event(drop.envelope(CONTEXT, "connection")).getAsJsonArray("items").get(0)
			.getAsJsonObject().getAsJsonObject("value").get("kind").getAsString());
		assertFalse(new DropCapture().capture(loot("Barrows", LootRecordType.EVENT),
			id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW).event().isPresent());
	}

	@Test public void reportsWhyEachRewardWasFilteredWithoutRetainingIt()
	{
		DropCapture capture = new DropCapture();
		LootReceived injected = loot("Barrows", LootRecordType.EVENT, new ItemStack(995, 50_000));
		injected.setMetadata(List.of(new ItemStack(995, 1)));
		LootReceived handled = loot("Guard", LootRecordType.NPC, new ItemStack(995, 50_000));
		capture.capture(handled, id -> definition(id, true, -1, -1), id -> 0, SESSION, NOW);
		Map<DropCaptureResult.Reason, DropCaptureResult> results = Map.of(
			DropCaptureResult.Reason.PLUGIN_INJECTION, capture.capture(injected, id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW),
			DropCaptureResult.Reason.UNSUPPORTED_SOURCE, capture.capture(loot("Unreviewed label", LootRecordType.EVENT, new ItemStack(995, 50_000)),
				id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW),
			DropCaptureResult.Reason.ALREADY_HANDLED, capture.capture(handled, id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW),
			DropCaptureResult.Reason.NO_ITEMS, capture.capture(loot("Guard", LootRecordType.NPC), id -> { throw new AssertionError(); }, id -> 0, SESSION, NOW));
		results.forEach((reason, result) ->
		{
			assertEquals(reason, result.reason());
			assertEquals(DropCaptureResult.Stage.FILTERED, result.stage());
			assertFalse(result.event().isPresent());
			assertEquals(0, result.itemStacks());
		});
		assertFalse(results.get(DropCaptureResult.Reason.PLUGIN_INJECTION).sourceType().isPresent());
		assertEquals("npc", results.get(DropCaptureResult.Reason.NO_ITEMS).sourceType().get());
	}

	@Test public void belowThresholdAndEligibleResultsCarryOnlyAggregateFacts()
	{
		DropCaptureResult low = new DropCapture().capture(loot("Guard", LootRecordType.NPC, new ItemStack(995, 49_999), new ItemStack(10000, 1), new ItemStack(10001, 1)),
			id -> definition(id, id != 10001, -1, -1), id -> 30_000, SESSION, NOW);
		assertEquals(DropCaptureResult.Reason.BELOW_THRESHOLD, low.reason());
		assertEquals(3, low.itemStacks());
		assertEquals(2, low.valuedStacks());
		assertEquals(Long.valueOf(49_999), low.highestStackGp().get());
		assertFalse(low.notable());
		assertFalse(low.event().isPresent());

		DropCaptureResult notable = new DropCapture().capture(loot("Vorkath", LootRecordType.NPC, new ItemStack(21907, 1)),
			id -> definition(id, false, -1, -1), id -> { throw new AssertionError(); }, SESSION, NOW);
		assertEquals(DropCaptureResult.Reason.ELIGIBLE, notable.reason());
		assertEquals("npc", notable.sourceType().get());
		assertTrue(notable.notable());
		assertEquals(0, notable.valuedStacks());
		assertFalse(notable.highestStackGp().isPresent());
		assertEquals(event(notable.event().get().envelope(CONTEXT, "connection")).get("eventId").getAsString(), notable.event().get().eventId());
		assertEquals(DropCaptureResult.Reason.SUBMITTED_FOR_LOCAL_SAVE, notable.submitted(true).reason());
		assertEquals(DropCaptureResult.Reason.LOCAL_SAVE_UNAVAILABLE, notable.submitted(false).reason());
		assertTrue(notable.submitted(false).notable());
	}

	static DropEvent drop()
	{
		return new DropCapture().capture(loot("Barrows", LootRecordType.EVENT, new ItemStack(995, 50_000)),
			id -> definition(id, true, -1, -1), id -> 0, SESSION, NOW).event().get();
	}

	static DropEvent notedDrop(long price)
	{
		return new DropCapture().capture(loot("Vorkath", LootRecordType.NPC, new ItemStack(22125, 2), new ItemStack(30000, 1)),
			id -> definition(id, id == 22124 || id == 30000, id == 22125 ? 799 : -1, id == 22125 ? 22124 : -1),
			id -> { if (id == 30000) return 50_000; assertEquals(22124, id); return price; }, SESSION, NOW).event().get();
	}

	static JsonObject event(JsonObject body) { return body.getAsJsonArray("events").get(0).getAsJsonObject(); }
	static LootReceived loot(String name, LootRecordType type, ItemStack... items)
	{
		return new LootReceived(name, 42, type, Arrays.asList(items), 3, "private metadata never sent");
	}

	static ItemComposition definition(int id, boolean tradeable, int note, int linked)
	{
		return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(), new Class<?>[]{ItemComposition.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getMembersName": return "Fixture item";
				case "getId": return id;
				case "isTradeable": return tradeable;
				case "getNote": return note;
				case "getLinkedNoteId": return linked;
				default: throw new AssertionError(method.getName());
			}
		});
	}
}

package com.runeglass.runelite;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

final class DropCapture
{
	static final Set<String> EVENTS = DropPolicy.REWARD_EVENTS;
	private final List<WeakReference<LootReceived>> observed = new ArrayList<>();

	DropCaptureResult capture(LootReceived loot, ItemManager itemManager, UUID sessionId, Instant now)
	{
		return capture(loot, itemManager::getItemComposition, id -> itemManager.getItemPriceWithSource(id, true), sessionId, now);
	}

	DropCaptureResult capture(LootReceived loot, IntFunction<ItemComposition> definitions,
		IntToLongFunction prices, UUID sessionId, Instant now)
	{
		if (loot.getMetadata() instanceof java.util.Collection)
		{
			return DropCaptureResult.filtered(DropCaptureResult.Reason.PLUGIN_INJECTION, null);
		}
		String sourceType;
		if (loot.getType() == LootRecordType.NPC) sourceType = "npc";
		else if (loot.getType() == LootRecordType.PICKPOCKET) sourceType = "pickpocket";
		else if (loot.getType() == LootRecordType.EVENT && EVENTS.contains(loot.getName())) sourceType = "event";
		else return DropCaptureResult.filtered(DropCaptureResult.Reason.UNSUPPORTED_SOURCE, null);
		if (!DropEvent.saneLabel(loot.getName()) || loot.getAmount() < 1 || loot.getAmount() > 10_000)
		{
			throw new IllegalArgumentException("Unsupported reward shape");
		}
		observed.removeIf(reference -> reference.get() == null);
		if (observed.stream().anyMatch(reference -> reference.get() == loot))
		{
			return DropCaptureResult.filtered(DropCaptureResult.Reason.ALREADY_HANDLED, sourceType);
		}
		if (observed.size() == 1_024) observed.remove(0);
		observed.add(new WeakReference<>(loot));
		Map<Integer, Integer> quantities = new TreeMap<>();
		for (ItemStack stack : loot.getItems())
		{
			if (stack.getId() <= 0 || stack.getQuantity() <= 0)
			{
				throw new IllegalArgumentException("Invalid reward quantity");
			}
			quantities.merge(stack.getId(), stack.getQuantity(), Math::addExact);
		}
		if (quantities.isEmpty()) return DropCaptureResult.filtered(DropCaptureResult.Reason.NO_ITEMS, sourceType);
		if (quantities.size() > 64) throw new IllegalArgumentException("Too many reward items");
		List<JsonObject> items = new ArrayList<>();
		long total = 0;
		boolean retained = false;
		boolean notable = false;
		int valuedStacks = 0;
		Long highestStackGp = null;
		for (Map.Entry<Integer, Integer> stack : quantities.entrySet())
		{
			int itemId = stack.getKey();
			ItemComposition composition = definitions.apply(itemId);
			if (composition == null || !DropEvent.saneLabel(composition.getMembersName()))
			{
				throw new IllegalArgumentException("Unavailable reward definition");
			}
			ItemComposition valuationComposition = composition;
			if (composition.getNote() != -1 && composition.getLinkedNoteId() > 0)
			{
				valuationComposition = definitions.apply(composition.getLinkedNoteId());
			}
			JsonObject value = new JsonObject();
			int marketItemId = 0;
			Long capturedStackGp = null;
			if (itemId == 995)
			{
				value.addProperty("kind", "coins");
				capturedStackGp = (long) stack.getValue();
			}
			else if (valuationComposition == null || valuationComposition.getId() <= 0 || valuationComposition.getId() == 995)
			{
				value.addProperty("kind", "unknown");
			}
			else if (!valuationComposition.isTradeable()) value.addProperty("kind", "untradeable");
			else
			{
				marketItemId = valuationComposition.getId();
				long price = prices.applyAsLong(marketItemId);
				if (price > Integer.MAX_VALUE) price = 0;
				value.addProperty("kind", price > 0 ? "ge" : "unknown");
				if (price > 0)
				{
					value.addProperty("unitPrice", price);
					capturedStackGp = price * stack.getValue();
				}
			}
			if (capturedStackGp != null)
			{
				total += capturedStackGp;
				valuedStacks++;
				highestStackGp = highestStackGp == null ? capturedStackGp : Math.max(highestStackGp, capturedStackGp);
			}
			if (total > 9_007_199_254_740_991L) throw new IllegalArgumentException("Reward value overflow");
			JsonObject item = new JsonObject();
			item.addProperty("itemId", itemId);
			if (marketItemId > 0) item.addProperty("marketItemId", marketItemId);
			item.addProperty("itemName", composition.getMembersName());
			item.addProperty("quantity", stack.getValue());
			item.add("value", value);
			items.add(item);
			retained |= DropPolicy.qualifies(sourceType, loot.getName(), itemId, capturedStackGp);
			notable |= DropPolicy.isNotableReward(sourceType, loot.getName(), itemId);
		}
		return DropCaptureResult.evaluated(sourceType, items.size(), valuedStacks, highestStackGp, notable,
			retained ? new DropEvent(UUID.randomUUID(), sessionId, now, sourceType, loot.getName(), loot.getAmount(), items) : null);
	}
}

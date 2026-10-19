package com.runeglass.runelite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.ArrayList;

final class DropEvent
{
	private final JsonObject json;

	DropEvent(UUID eventId, UUID sessionId, Instant occurredAt, String sourceType,
		String sourceName, int rollCount, List<JsonObject> items)
	{
		this(eventId, sessionId, occurredAt, sourceType, sourceName, rollCount, items, null);
	}

	DropEvent(UUID eventId, UUID sessionId, Instant occurredAt, String sourceType,
		String sourceName, int rollCount, List<JsonObject> items, Integer npcId)
	{
		json = new JsonObject();
		json.addProperty("eventId", eventId.toString());
		json.addProperty("sessionId", sessionId.toString());
		json.addProperty("occurredAt", occurredAt.toString());
		JsonObject source = new JsonObject();
		source.addProperty("type", sourceType);
		source.addProperty("name", sourceName);
		if ("npc".equals(sourceType) && npcId != null && npcId > 0) source.addProperty("npcId", npcId);
		json.add("source", source);
		json.addProperty("rollCount", rollCount);
		JsonArray rewards = new JsonArray();
		items.forEach(item -> rewards.add(item.deepCopy()));
		json.add("items", rewards);
	}

	private DropEvent(JsonObject json) { this.json = json.deepCopy(); }

	String sourceType() { return json.getAsJsonObject("source").get("type").getAsString(); }
	String sourceName() { return json.getAsJsonObject("source").get("name").getAsString(); }
	int rollCount() { return json.get("rollCount").getAsInt(); }
	List<Integer> itemIdsNamed(String name)
	{
		List<Integer> ids = new ArrayList<>();
		json.getAsJsonArray("items").forEach(element ->
		{
			JsonObject item = element.getAsJsonObject();
			if (name.equals(item.get("itemName").getAsString())) ids.add(item.get("itemId").getAsInt());
		});
		return ids;
	}

	DropEvent freeze(DropCountParser.Count count, JsonObject continuity, String collectionLog, Set<Integer> newSlots)
	{
		if (json.has("collectionLog")) throw new IllegalStateException("Drop already frozen");
		JsonObject frozen = json.deepCopy();
		if (count != null)
		{
			JsonObject sourceCount = new JsonObject();
			sourceCount.addProperty("value", count.value);
			sourceCount.addProperty("unit", count.counter.unit);
			sourceCount.addProperty("counterKey", count.counter.key);
			sourceCount.addProperty("evidence", "game-message");
			frozen.add("sourceCount", sourceCount);
			if (continuity != null) frozen.add("continuity", continuity.deepCopy());
		}
		frozen.addProperty("collectionLog", collectionLog);
		frozen.getAsJsonArray("items").forEach(element ->
		{
			JsonObject item = element.getAsJsonObject();
			if (newSlots.contains(item.get("itemId").getAsInt())) item.addProperty("newCollectionLogSlot", true);
		});
		return new DropEvent(frozen);
	}

	String eventId()
	{
		return json.get("eventId").getAsString();
	}

	JsonObject envelope(SyncContext context, String connectionId)
	{
		JsonObject batch = new JsonObject();
		batch.addProperty("protocolVersion", 1);
		batch.addProperty("consentVersion", 1);
		batch.addProperty("connectionId", connectionId);
		JsonObject client = new JsonObject();
		client.addProperty("pluginVersion", context.getPluginVersion());
		client.addProperty("runeliteVersion", context.getRuneLiteVersion());
		client.addProperty("gameRevision", context.getGameRevision());
		batch.add("client", client);
		JsonObject character = new JsonObject();
		character.addProperty("displayName", context.getDisplayName());
		character.addProperty("accountType", context.getAccountType());
		character.addProperty("profileType", context.getProfileType());
		batch.add("character", character);
		JsonArray events = new JsonArray();
		events.add(json.deepCopy());
		batch.add("events", events);
		return batch;
	}

	static boolean saneLabel(String label)
	{
		return label != null && !label.trim().isEmpty() && label.length() <= 120
			&& label.matches("[^<>\\x00-\\x1f\\x7f]+");
	}
}

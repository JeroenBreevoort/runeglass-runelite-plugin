package com.runeglass.runelite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class DropEvent
{
	private final JsonObject json;

	DropEvent(UUID eventId, UUID sessionId, Instant occurredAt, String sourceType,
		String sourceName, int rollCount, List<JsonObject> items)
	{
		json = new JsonObject();
		json.addProperty("eventId", eventId.toString());
		json.addProperty("sessionId", sessionId.toString());
		json.addProperty("occurredAt", occurredAt.toString());
		JsonObject source = new JsonObject();
		source.addProperty("type", sourceType);
		source.addProperty("name", sourceName);
		json.add("source", source);
		json.addProperty("rollCount", rollCount);
		JsonArray rewards = new JsonArray();
		items.forEach(rewards::add);
		json.add("items", rewards);
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

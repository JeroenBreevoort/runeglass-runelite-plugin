package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

final class DurableDropQueue
{
	static final int MAX_BATCH_BYTES = 64 * 1_024;
	static final long MAX_BYTES = 5 * 1_024 * 1_024;
	static final int MAX_EVENTS = 10_000;
	private final Filepath directory;
	private final Gson gson;
	private final String connectionId;
	private final List<Entry> entries = new ArrayList<>();
	private long bytes;
	private long sequence;
	private int fileCount;
	private boolean damaged;

	DurableDropQueue(Filepath root, String connectionId, Gson gson) throws IOException
	{
		if (!connectionId.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("Invalid connection");
		this.directory = root.joinSegment(connectionId);
		this.connectionId = connectionId;
		this.gson = gson;
		PrivateFiles.createDirectory(directory);
		try (Stream<Filepath> files = directory.walk(1))
		{
			for (Filepath path : files.filter(file -> !file.equals(directory)).sorted().collect(Collectors.toList()))
			{
				if (DropContinuityStore.FILE.equals(path.getFileName())) continue;
				fileCount++;
				long size = path.size();
				bytes += size;
				String name = path.getFileName();
				try
				{
					if (!name.matches("[0-9]{20}-[a-f0-9-]{36}\\.json")) throw new IOException("Uncommitted record");
					sequence = Math.max(sequence, Long.parseLong(name.substring(0, 20)));
					if (size > MAX_BATCH_BYTES) throw new IOException("Oversized record");
					JsonObject body = gson.fromJson(PrivateFiles.readUtf8(path), JsonObject.class);
					validate(body, connectionId);
					String id = body.getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
					if (!name.substring(21, 57).equals(id)) throw new IOException("Record identity mismatch");
					entries.add(new Entry(path, body, size));
				}
				catch (IOException | RuntimeException exception)
				{
					damaged = true;
				}
			}
		}
	}

	void append(JsonObject body) throws IOException
	{
		validate(body, connectionId);
		byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
		if (data.length > MAX_BATCH_BYTES) throw new IOException("Drop payload exceeds limit");
		if (fileCount >= MAX_EVENTS || bytes + data.length > MAX_BYTES) throw new CapacityException();
		String id = body.getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
		String name = String.format(java.util.Locale.ROOT, "%020d-%s.json", (sequence = Math.incrementExact(sequence)), id);
		Filepath target = directory.joinSegment(name);
		PrivateFiles.write(target, name + ".", data);
		entries.add(new Entry(target, body.deepCopy(), data.length));
		bytes += data.length;
		fileCount++;
	}

	List<Entry> batch(boolean individual)
	{
		List<Entry> batch = new ArrayList<>();
		for (Entry entry : entries)
		{
			if (!batch.isEmpty() && !sameEnvelope(batch.get(0).body, entry.body)) break;
			batch.add(entry);
			if (gson.toJson(payload(batch)).getBytes(StandardCharsets.UTF_8).length > MAX_BATCH_BYTES)
			{
				batch.remove(batch.size() - 1);
				break;
			}
			if (batch.size() == (individual ? 1 : 20)) break;
		}
		return batch;
	}

	static JsonObject payload(List<Entry> batch)
	{
		JsonObject body = batch.get(0).body.deepCopy();
		JsonArray events = new JsonArray();
		batch.forEach(entry -> events.add(entry.body.getAsJsonArray("events").get(0).deepCopy()));
		body.add("events", events);
		return body;
	}

	void acknowledge(List<Entry> batch, Set<String> ids) throws IOException
	{
		for (Entry entry : batch)
		{
			if (!ids.contains(entry.id())) continue;
			entry.path.delete();
			entries.remove(entry);
			bytes -= entry.bytes;
			fileCount--;
		}
	}

	void block(Entry entry) throws IOException
	{
		entry.path.moveTo(entry.path.getParent().joinSegment(entry.path.getFileName() + ".blocked"), StandardCopyOption.ATOMIC_MOVE);
		entries.remove(entry);
		damaged = true;
	}

	int size() { return entries.size(); }
	boolean hasDamagedRecords() { return damaged; }

	static Filepath discardMarker(Filepath directory)
	{
		return directory.getParent().joinSegment(directory.getFileName() + ".discard");
	}

	static void markDiscard(Filepath directory) throws IOException
	{
		Filepath marker = discardMarker(directory);
		PrivateFiles.createDirectory(marker.getParent());
		PrivateFiles.write(marker, marker.getFileName() + ".", new byte[]{1});
	}

	static void purge(Filepath root) throws IOException
	{
		if (root.exists()) root.deleteRecursively();
	}

	private static boolean sameEnvelope(JsonObject left, JsonObject right)
	{
		return left.get("connectionId").equals(right.get("connectionId"))
			&& left.get("client").equals(right.get("client"))
			&& left.get("character").equals(right.get("character"));
	}

	private static void validate(JsonObject body, String connectionId) throws IOException
	{
		require(body != null && ProtocolJson.hasExactKeys(body, "protocolVersion", "consentVersion", "connectionId", "client", "character", "events"));
		require(ProtocolJson.intValue(body, "protocolVersion") == 1 && ProtocolJson.intValue(body, "consentVersion") == 1
			&& connectionId.equals(ProtocolJson.stringValue(body, "connectionId")) && body.getAsJsonArray("events").size() == 1);
		JsonObject client = body.getAsJsonObject("client");
		require(ProtocolJson.hasExactKeys(client, "pluginVersion", "runeliteVersion", "gameRevision")
			&& ProtocolJson.stringValue(client, "pluginVersion").matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")
			&& ProtocolJson.stringValue(client, "pluginVersion").length() <= 64
			&& !ProtocolJson.stringValue(client, "runeliteVersion").isEmpty()
			&& ProtocolJson.stringValue(client, "runeliteVersion").length() <= 64
			&& ProtocolJson.intValue(client, "gameRevision") >= 0 && ProtocolJson.intValue(client, "gameRevision") <= 1_000_000);
		JsonObject character = body.getAsJsonObject("character");
		require(ProtocolJson.hasExactKeys(character, "displayName", "accountType", "profileType")
			&& ProtocolJson.stringValue(character, "displayName").matches("[A-Za-z0-9 _-]{1,12}")
			&& Set.of("regular", "ironman", "hardcore", "ultimate").contains(ProtocolJson.stringValue(character, "accountType"))
			&& "standard".equals(ProtocolJson.stringValue(character, "profileType")));
		JsonObject event = body.getAsJsonArray("events").get(0).getAsJsonObject();
		require(ProtocolJson.hasKeys(event, Set.of("eventId", "sessionId", "occurredAt", "source", "rollCount", "items"), Set.of("sourceCount", "continuity", "collectionLog")));
		for (String field : List.of("eventId", "sessionId"))
		{
			require(ProtocolJson.stringValue(event, field).matches("[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"));
		}
		require(java.time.Instant.parse(ProtocolJson.stringValue(event, "occurredAt")).toEpochMilli() > 0);
		require(ProtocolJson.intValue(event, "rollCount") >= 1 && ProtocolJson.intValue(event, "rollCount") <= 10_000);
		JsonObject source = event.getAsJsonObject("source");
		require(ProtocolJson.hasKeys(source, Set.of("type", "name"), Set.of("npcId")) && DropEvent.saneLabel(ProtocolJson.stringValue(source, "name"))
			&& Set.of("npc", "pickpocket", "event").contains(ProtocolJson.stringValue(source, "type")));
		require(!"event".equals(ProtocolJson.stringValue(source, "type")) || DropCapture.EVENTS.contains(ProtocolJson.stringValue(source, "name")));
		if (source.has("npcId")) require("npc".equals(ProtocolJson.stringValue(source, "type")) && ProtocolJson.intValue(source, "npcId") > 0);
		if (event.has("sourceCount"))
		{
			JsonObject count = event.getAsJsonObject("sourceCount");
			DropCounterRegistry.Counter counter = DropCounterRegistry.byKey(ProtocolJson.stringValue(count, "counterKey"));
			require(ProtocolJson.hasExactKeys(count, "value", "unit", "counterKey", "evidence")
				&& counter != null && counter.unit.equals(ProtocolJson.stringValue(count, "unit"))
				&& counter.matches(ProtocolJson.stringValue(source, "type"), ProtocolJson.stringValue(source, "name"))
				&& ProtocolJson.intValue(count, "value") > 0 && "game-message".equals(ProtocolJson.stringValue(count, "evidence"))
				&& ProtocolJson.intValue(event, "rollCount") == 1);
		}
		if (event.has("continuity"))
		{
			JsonObject continuity = event.getAsJsonObject("continuity");
			require(event.has("sourceCount") && ProtocolJson.hasExactKeys(continuity, "id", "from")
				&& ProtocolJson.stringValue(continuity, "id").matches("[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
				&& ProtocolJson.intValue(continuity, "from") > 0
				&& ProtocolJson.intValue(continuity, "from") <= ProtocolJson.intValue(event.getAsJsonObject("sourceCount"), "value"));
		}
		if (event.has("collectionLog")) require(Set.of("enabled", "disabled", "unknown").contains(ProtocolJson.stringValue(event, "collectionLog")));
		JsonArray items = event.getAsJsonArray("items");
		require(items.size() >= 1 && items.size() <= 64);
		Set<Integer> ids = new HashSet<>();
		long total = 0;
		for (JsonElement element : items)
		{
			JsonObject item = element.getAsJsonObject();
			int id = ProtocolJson.intValue(item, "itemId");
			int quantity = ProtocolJson.intValue(item, "quantity");
			boolean hasMarketItemId = item.has("marketItemId");
			boolean validKeys = ProtocolJson.hasKeys(item, Set.of("itemId", "itemName", "quantity", "value"), Set.of("marketItemId", "newCollectionLogSlot"));
			if (item.has("newCollectionLogSlot")) require(item.get("newCollectionLogSlot").isJsonPrimitive()
				&& item.get("newCollectionLogSlot").getAsJsonPrimitive().isBoolean() && item.get("newCollectionLogSlot").getAsBoolean());
			require(validKeys
				&& id > 0 && ids.add(id) && quantity > 0 && DropEvent.saneLabel(ProtocolJson.stringValue(item, "itemName")));
			JsonObject value = item.getAsJsonObject("value");
			String kind = ProtocolJson.stringValue(value, "kind");
			require((id == 995) == "coins".equals(kind));
			if (hasMarketItemId)
			{
				int marketItemId = ProtocolJson.intValue(item, "marketItemId");
				require(marketItemId > 0 && marketItemId != 995 && ("ge".equals(kind) || "unknown".equals(kind)));
			}
			if ("ge".equals(kind))
			{
				int price = ProtocolJson.intValue(value, "unitPrice");
				require(ProtocolJson.hasExactKeys(value, "kind", "unitPrice") && price >= 0);
				total += (long) price * quantity;
			}
			else
			{
				require(ProtocolJson.hasExactKeys(value, "kind") && Set.of("coins", "untradeable", "unknown").contains(kind));
				if ("coins".equals(kind)) total += quantity;
			}
			require(total <= 9_007_199_254_740_991L);
		}
	}

	private static void require(boolean valid) throws IOException
	{
		if (!valid) throw new IOException("Invalid stored drop");
	}

	static final class Entry
	{
		final Filepath path;
		final JsonObject body;
		final long bytes;
		Entry(Filepath path, JsonObject body, long bytes) { this.path = path; this.body = body; this.bytes = bytes; }
		String id() { return body.getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString(); }
	}

	static final class CapacityException extends IOException
	{
		private static final long serialVersionUID = 1L;
		CapacityException() { super("Local drop queue is full"); }
	}
}

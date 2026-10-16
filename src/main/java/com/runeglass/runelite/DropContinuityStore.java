package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.runelite.client.util.Filepath;

final class DropContinuityStore
{
	static final String FILE = "continuity-v1.json";
	private final Filepath file;
	private final Gson gson;
	private final String profileKey;
	private JsonObject counters = new JsonObject();
	private boolean damaged;

	DropContinuityStore(Filepath directory, Gson gson, String profileKey) throws IOException
	{
		this.file = directory.joinSegment(FILE);
		this.gson = gson;
		this.profileKey = profileKey;
		if (profileKey == null || profileKey.isEmpty() || profileKey.length() > 256) throw new IOException("Invalid continuity scope");
		PrivateFiles.createDirectory(directory);
		if (!file.exists()) return;
		try
		{
			if (file.size() > 64 * 1024) throw new IOException("Oversized continuity");
			JsonObject body = gson.fromJson(PrivateFiles.readUtf8(file), JsonObject.class);
			if (!ProtocolJson.hasExactKeys(body, "version", "profile", "counters")
				|| ProtocolJson.intValue(body, "version") != 1) throw new IOException("Invalid continuity");
			if (!profileKey.equals(ProtocolJson.stringValue(body, "profile"))) return;
			JsonObject restored = body.getAsJsonObject("counters");
			if (restored.size() > DropCounterRegistry.COUNTERS.size()) throw new IOException("Too many counters");
			for (java.util.Map.Entry<String, JsonElement> entry : restored.entrySet())
			{
				JsonObject state = entry.getValue().getAsJsonObject();
				if (DropCounterRegistry.byKey(entry.getKey()) == null || !ProtocolJson.hasExactKeys(state, "id", "from", "last")
					|| !ProtocolJson.stringValue(state, "id").matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
					|| ProtocolJson.intValue(state, "from") < 1 || ProtocolJson.intValue(state, "last") < ProtocolJson.intValue(state, "from"))
					throw new IOException("Invalid continuity state");
			}
			counters = restored;
		}
		catch (IOException | RuntimeException exception)
		{
			damaged = true;
			counters = new JsonObject();
		}
	}

	JsonObject observe(DropCountParser.Count count)
	{
		JsonObject previous = counters.getAsJsonObject(count.counter.key);
		int last = previous == null ? 0 : ProtocolJson.intValue(previous, "last");
		boolean reset = previous == null || count.value <= last;
		JsonObject next = new JsonObject();
		next.addProperty("id", reset ? UUID.randomUUID().toString() : ProtocolJson.stringValue(previous, "id"));
		next.addProperty("from", !reset && (long) last + 1 == count.value ? ProtocolJson.intValue(previous, "from") : count.value);
		next.addProperty("last", count.value);
		counters.add(count.counter.key, next);
		JsonObject evidence = next.deepCopy();
		evidence.remove("last");
		return evidence;
	}

	void invalidate(String type, String name)
	{
		for (DropCounterRegistry.Counter counter : DropCounterRegistry.COUNTERS)
			if (counter.matches(type, name)) counters.remove(counter.key);
	}

	void invalidateAll() { counters = new JsonObject(); }
	boolean damaged() { return damaged; }

	void save() throws IOException
	{
		JsonObject body = new JsonObject();
		body.addProperty("version", 1);
		body.addProperty("profile", profileKey);
		body.add("counters", counters);
		PrivateFiles.write(file, FILE + ".", gson.toJson(body).getBytes(StandardCharsets.UTF_8));
	}
}
